package whatsbot

// stub.go — test/stub WhatsApp client for Android UI development and unit testing.
//
// No build tag: included in all builds (including the gomobile .aar) so that
// Android debug builds can use NewTestClient without a real WhatsApp connection.

import (
	"database/sql"
	"fmt"
	"time"

	_ "github.com/mattn/go-sqlite3"
)

// stubChats defines the three individual chats seeded by NewTestClient.
var stubChats = []struct {
	jid  string
	name string
}{
	{"6591234567@s.whatsapp.net", "Alice Tan"},
	{"6598765432@s.whatsapp.net", "Bob Lim"},
	{"6587654321@s.whatsapp.net", "Charlie Ng"},
}

const stubGroupJID = "120363000000000001@g.us"
const stubGroupName = "Family Group"

// stubGroupMembers are the three members of the seeded group chat.
var stubGroupMembers = []string{
	"6591234567@s.whatsapp.net",
	"6598765432@s.whatsapp.net",
	"6587654321@s.whatsapp.net",
}

// stubConversations defines the 5 messages per chat (individual × 3 + group × 1).
// Each entry: {chatJID, senderJID, body, isFromMe, daysAgo}
type stubMsg struct {
	chatJID   string
	senderJID string
	body      string
	isFromMe  bool
	isGroup   bool
	daysAgo   int
}

func buildStubMessages() []stubMsg {
	own := "6500000000@s.whatsapp.net" // placeholder self JID for stub

	msgs := []stubMsg{}
	for _, chat := range stubChats {
		// 5 messages per individual chat, mix of sent and received
		msgs = append(msgs,
			stubMsg{chat.jid, chat.jid, "Hey, how are you?", false, false, 7},
			stubMsg{chat.jid, own, "I'm good, thanks! And you?", true, false, 6},
			stubMsg{chat.jid, chat.jid, "Doing great, thanks for asking.", false, false, 5},
			stubMsg{chat.jid, own, "Want to meet up this weekend?", true, false, 3},
			stubMsg{chat.jid, chat.jid, "Sure, sounds good!", false, false, 1},
		)
	}
	// 5 messages in the group chat
	msgs = append(msgs,
		stubMsg{stubGroupJID, stubGroupMembers[0], "Good morning everyone!", false, true, 7},
		stubMsg{stubGroupJID, stubGroupMembers[1], "Morning! 😊", false, true, 6},
		stubMsg{stubGroupJID, own, "Morning! Anyone free this Saturday?", true, true, 5},
		stubMsg{stubGroupJID, stubGroupMembers[2], "I'm free in the afternoon.", false, true, 3},
		stubMsg{stubGroupJID, stubGroupMembers[0], "Let's meet at 3pm then!", false, true, 1},
	)
	return msgs
}

// seedStubDB populates wa_chats, wa_group_members, and wa_messages with
// realistic fake Singapore data. Called only from NewTestClient.
func seedStubDB(db *sql.DB) error {
	now := time.Now()

	// Individual chats
	for _, chat := range stubChats {
		upsertChat(db, chat.jid, chat.name, "", false, false, false,
			uint64(now.Unix()), uint64(now.Add(-30*24*time.Hour).Unix()),
			"", 0)
	}

	// Group chat
	upsertChat(db, stubGroupJID, stubGroupName, "Family coordination group",
		true, false, false,
		uint64(now.Unix()), uint64(now.Add(-90*24*time.Hour).Unix()),
		stubGroupMembers[0], 0)
	for _, m := range stubGroupMembers {
		rank := "regular"
		if m == stubGroupMembers[0] {
			rank = "admin"
		}
		upsertGroupMember(db, stubGroupJID, m, rank)
	}

	// Messages
	for i, m := range buildStubMessages() {
		ts := now.Add(-time.Duration(m.daysAgo)*24*time.Hour + time.Duration(i)*time.Minute)
		msgID := fmt.Sprintf("stub-msg-%04d", i)
		storeMessage(db, msgID, m.chatJID, m.senderJID, ts,
			m.body, "", "", "", m.isFromMe, m.isGroup, false)
	}
	return nil
}

// NewTestClient creates a Client that is NOT connected to WhatsApp.
// It opens (or creates) a SQLite database at dbPath, runs the same schema
// migrations as NewClient, and seeds the database with realistic fake data.
//
// Pass ":memory:" as dbPath for a fully in-memory database (discarded when the
// client is closed), which is the recommended choice for unit tests.
//
// IsStub() returns true on the returned client. Network methods (Connect,
// Disconnect, Logout, SendMessage) must not be called on a stub client.
func NewTestClient(dbPath string) (*Client, error) {
	dsn := "file:" + dbPath + "?_foreign_keys=on&_journal_mode=WAL&_busy_timeout=5000"
	if dbPath == ":memory:" {
		dsn = ":memory:?_foreign_keys=on"
	}
	db, err := sql.Open("sqlite3", dsn)
	if err != nil {
		return nil, fmt.Errorf("stub open db: %w", err)
	}

	if err := initMessageTable(db); err != nil {
		db.Close()
		return nil, fmt.Errorf("stub init message table: %w", err)
	}

	if err := seedStubDB(db); err != nil {
		db.Close()
		return nil, fmt.Errorf("stub seed db: %w", err)
	}

	return &Client{
		db:    db,
		phone: "stub",
		stub:  true,
	}, nil
}

// IsStub reports whether this client was created by NewTestClient.
// Android code can gate real-connection UI on !client.IsStub().
func (c *Client) IsStub() bool {
	return c.stub
}

// SimulateIncomingMessage constructs a Message with the given fields and delivers
// it to the registered Listener (via OnMessage) and stores it in the database.
// It is a no-op when called on a non-stub client.
func (c *Client) SimulateIncomingMessage(jid, senderName, text string) {
	if !c.stub {
		return
	}
	now := time.Now()
	msgID := fmt.Sprintf("stub-sim-%d", now.UnixNano())
	storeMessage(c.db, msgID, jid, jid, now,
		text, "", "", senderName, false, false, false)

	msg := &Message{
		ID:        msgID,
		ChatJID:   jid,
		SenderJID: jid,
		Body:      text,
		MediaType: "",
		Timestamp: now.Unix(),
		FromMe:    false,
		IsGroup:   false,
	}
	c.emit(func(l Listener) { l.OnMessage(msg) })
}

// SimulateActivity stores the current physical activity for the rules engine to
// read. Valid values: "IN_VEHICLE", "ON_BICYCLE", "RUNNING", "WALKING", "STILL".
// It is a no-op when called on a non-stub client.
func (c *Client) SimulateActivity(activity string) {
	if !c.stub {
		return
	}
	switch activity {
	case "IN_VEHICLE", "ON_BICYCLE", "RUNNING", "WALKING", "STILL":
		c.mu.Lock()
		c.activity = activity
		c.mu.Unlock()
	}
}

// GetActivity returns the last activity set by SimulateActivity.
// Returns an empty string when no activity has been set or on non-stub clients.
func (c *Client) GetActivity() string {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.activity
}
