package whatsbot

import (
	"sync"
	"testing"
)

// captureListener collects OnMessage calls for assertions in stub tests.
type captureListener struct {
	mu       sync.Mutex
	messages []*Message
}

func (l *captureListener) OnConnected()            {}
func (l *captureListener) OnDisconnected()         {}
func (l *captureListener) OnPairingCode(_ string)  {}
func (l *captureListener) OnPaired(_ string)       {}
func (l *captureListener) OnHistorySync(_ int)     {}
func (l *captureListener) OnLoggedOut()            {}
func (l *captureListener) OnError(_ string)        {}
func (l *captureListener) OnMessage(msg *Message)  {
	l.mu.Lock()
	l.messages = append(l.messages, msg)
	l.mu.Unlock()
}

func (l *captureListener) collected() []*Message {
	l.mu.Lock()
	defer l.mu.Unlock()
	out := make([]*Message, len(l.messages))
	copy(out, l.messages)
	return out
}

// TestNewTestClient verifies that NewTestClient seeds the expected chats and
// messages into the database without requiring a real WhatsApp connection.
func TestNewTestClient(t *testing.T) {
	c, err := NewTestClient(":memory:")
	if err != nil {
		t.Fatalf("NewTestClient: %v", err)
	}
	defer c.db.Close()

	if !c.IsStub() {
		t.Fatal("expected IsStub() == true")
	}

	// Expect 4 chats: 3 individual + 1 group.
	var chatCount int
	if err := c.db.QueryRow(`SELECT COUNT(*) FROM wa_chats`).Scan(&chatCount); err != nil {
		t.Fatalf("count wa_chats: %v", err)
	}
	if chatCount != 4 {
		t.Errorf("expected 4 chats, got %d", chatCount)
	}

	// Expect 20 messages: 5 per chat × 4 chats.
	var msgCount int
	if err := c.db.QueryRow(`SELECT COUNT(*) FROM wa_messages`).Scan(&msgCount); err != nil {
		t.Fatalf("count wa_messages: %v", err)
	}
	if msgCount != 20 {
		t.Errorf("expected 20 messages, got %d", msgCount)
	}

	// Verify the group chat exists by name.
	var groupName string
	err = c.db.QueryRow(
		`SELECT name FROM wa_chats WHERE jid = ?`, stubGroupJID,
	).Scan(&groupName)
	if err != nil {
		t.Fatalf("query group chat: %v", err)
	}
	if groupName != stubGroupName {
		t.Errorf("expected group name %q, got %q", stubGroupName, groupName)
	}

	// Verify the group has 3 members.
	var memberCount int
	if err := c.db.QueryRow(
		`SELECT COUNT(*) FROM wa_group_members WHERE chat_jid = ?`, stubGroupJID,
	).Scan(&memberCount); err != nil {
		t.Fatalf("count group members: %v", err)
	}
	if memberCount != 3 {
		t.Errorf("expected 3 group members, got %d", memberCount)
	}
}

// TestSimulateIncomingMessage verifies that a simulated message is delivered to
// the registered Listener and stored in the database.
func TestSimulateIncomingMessage(t *testing.T) {
	c, err := NewTestClient(":memory:")
	if err != nil {
		t.Fatalf("NewTestClient: %v", err)
	}
	defer c.db.Close()

	cap := &captureListener{}
	c.SetListener(cap)

	const testJID = "6591234567@s.whatsapp.net"
	const testName = "Alice Tan"
	const testText = "Hello from the stub!"

	c.SimulateIncomingMessage(testJID, testName, testText)

	// Listener must have received exactly one new message (in addition to seed data,
	// which was inserted before the listener was registered).
	msgs := cap.collected()
	if len(msgs) != 1 {
		t.Fatalf("expected 1 listener message, got %d", len(msgs))
	}
	m := msgs[0]
	if m.ChatJID != testJID {
		t.Errorf("ChatJID: want %q, got %q", testJID, m.ChatJID)
	}
	if m.Body != testText {
		t.Errorf("Body: want %q, got %q", testText, m.Body)
	}
	if m.FromMe {
		t.Error("expected FromMe == false")
	}

	// Row must be present in the database.
	var dbBody string
	err = c.db.QueryRow(
		`SELECT body FROM wa_messages WHERE id = ?`, m.ID,
	).Scan(&dbBody)
	if err != nil {
		t.Fatalf("query simulated message: %v", err)
	}
	if dbBody != testText {
		t.Errorf("DB body: want %q, got %q", testText, dbBody)
	}
}

// TestRealClientIsNotStub verifies that a Client created by NewClient has
// IsStub() == false. We construct it with a valid phone but no network; the
// constructor only fails at the DB/schema level — IsStub is set before any
// network call, so we can check it even if network initialisation would fail
// (which it won't, since NewClient doesn't connect in the constructor).
func TestRealClientIsNotStub(t *testing.T) {
	// Use an in-memory DB path unique to this test so it does not conflict with
	// any other open handle. The sqlstore upgrade will run against an empty DB.
	c, err := NewClient(":memory:", "6500000000")
	if err != nil {
		// If the constructor errors for reasons beyond our control (e.g. the
		// whatsmeow sqlstore rejects an in-memory URI), skip rather than fail.
		t.Skipf("NewClient returned error (environment constraint): %v", err)
	}
	defer c.db.Close()

	if c.IsStub() {
		t.Error("NewClient must return IsStub() == false")
	}
}

// TestSimulateActivityStoredAndRead verifies that SimulateActivity stores the
// activity and GetActivity returns it; invalid values are ignored.
func TestSimulateActivityStoredAndRead(t *testing.T) {
	c, err := NewTestClient(":memory:")
	if err != nil {
		t.Fatalf("NewTestClient: %v", err)
	}
	defer c.db.Close()

	if got := c.GetActivity(); got != "" {
		t.Errorf("initial activity: want \"\", got %q", got)
	}

	c.SimulateActivity("WALKING")
	if got := c.GetActivity(); got != "WALKING" {
		t.Errorf("after WALKING: want %q, got %q", "WALKING", got)
	}

	c.SimulateActivity("IN_VEHICLE")
	if got := c.GetActivity(); got != "IN_VEHICLE" {
		t.Errorf("after IN_VEHICLE: want %q, got %q", "IN_VEHICLE", got)
	}

	// Invalid value must be silently ignored; activity stays as last valid value.
	c.SimulateActivity("FLYING")
	if got := c.GetActivity(); got != "IN_VEHICLE" {
		t.Errorf("after invalid: want %q, got %q", "IN_VEHICLE", got)
	}
}

// TestSimulateActivityNoopOnRealClient verifies that SimulateActivity is a
// no-op when called on a non-stub client.
func TestSimulateActivityNoopOnRealClient(t *testing.T) {
	c, err := NewClient(":memory:", "6500000000")
	if err != nil {
		t.Skipf("NewClient returned error (environment constraint): %v", err)
	}
	defer c.db.Close()

	c.SimulateActivity("WALKING") // must be a no-op, not a panic
	if got := c.GetActivity(); got != "" {
		t.Errorf("expected empty activity on real client, got %q", got)
	}
}
