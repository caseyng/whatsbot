// Package whatsbot wraps caseyng/whatsmeow with a gomobile-compatible API.
//
// Usage (from Go):
//
//	client, err := whatsbot.NewClient("/data/whatsbot.db", "6591234567")
//	client.SetListener(myListener)
//	err = client.Connect()     // blocks until TCP+TLS established; pairing code delivered via listener
//	...
//	client.SendMessage("6599887766@s.whatsapp.net", "hello")
//	...
//	client.Close()
//
// From Kotlin via gomobile, the same API is available as go.whatsbot.Client.
package whatsbot

import (
	"context"
	"fmt"
	"strings"
	"sync"
	"time"

	_ "github.com/mattn/go-sqlite3"
	"go.mau.fi/whatsmeow"
	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/store/sqlstore"
	"go.mau.fi/whatsmeow/types"
	"go.mau.fi/whatsmeow/types/events"
	waLog "go.mau.fi/whatsmeow/util/log"
	"google.golang.org/protobuf/proto"

	"database/sql"
)

// Client manages a single WhatsApp companion device session.
// It is safe to call methods on Client from multiple goroutines.
type Client struct {
	inner    *whatsmeow.Client
	db       *sql.DB
	phone    string
	mu       sync.RWMutex
	listener Listener
}

// NewClient opens or creates the SQLite database at dbPath and prepares a
// Client for the given phone number (digits only, e.g. "6591234567").
//
// Returns an error if:
//   - the database cannot be opened or migrated
//   - dbPath already contains a session for a different phone number
func NewClient(dbPath, phone string) (*Client, error) {
	db, err := sql.Open("sqlite3",
		"file:"+dbPath+"?_foreign_keys=on&_journal_mode=WAL")
	if err != nil {
		return nil, fmt.Errorf("open db: %w", err)
	}

	container := sqlstore.NewWithDB(db, "sqlite3", waLog.Noop)
	if err := container.Upgrade(context.Background()); err != nil {
		db.Close()
		return nil, fmt.Errorf("upgrade db: %w", err)
	}
	if err := initMessageTable(db); err != nil {
		db.Close()
		return nil, fmt.Errorf("init message table: %w", err)
	}

	device, err := container.GetFirstDevice(context.Background())
	if err != nil {
		db.Close()
		return nil, fmt.Errorf("get device: %w", err)
	}
	if device.ID != nil && device.ID.User != phone {
		db.Close()
		return nil, fmt.Errorf(
			"database already has a session for %s, not %s — use a different file",
			device.ID.User, phone)
	}

	inner := whatsmeow.NewClient(device, waLog.Noop)
	chrome := whatsmeow.NewChromeHTTPClient()
	inner.SetWebsocketHTTPClient(chrome)
	inner.SetPreLoginHTTPClient(chrome)

	c := &Client{inner: inner, db: db, phone: phone}
	inner.AddEventHandler(c.handleEvent)
	return c, nil
}

// SetListener registers the event receiver. Safe to call before or after Connect.
func (c *Client) SetListener(l Listener) {
	c.mu.Lock()
	c.listener = l
	c.mu.Unlock()
}

// Connect establishes the WebSocket connection to WhatsApp.
//
// If no session exists, Connect requests a pairing code and delivers it via
// Listener.OnPairingCode. The user enters the code in WhatsApp → Linked
// Devices → Link with Phone Number. Listener.OnPaired fires when done.
//
// If a session already exists, Connect resumes it. Listener.OnConnected
// fires when authentication completes.
//
// Connect returns once the WebSocket is up; it does not block for pairing.
func (c *Client) Connect() error {
	if err := c.inner.Connect(); err != nil {
		return fmt.Errorf("connect: %w", err)
	}
	if c.inner.Store.ID == nil {
		code, err := c.inner.PairPhone(
			context.Background(), c.phone, true,
			whatsmeow.PairClientChrome, "Chrome (Linux)")
		if err != nil {
			return fmt.Errorf("pair phone: %w", err)
		}
		c.emit(func(l Listener) { l.OnPairingCode(code) })
	}
	return nil
}

// Disconnect closes the WebSocket without removing the session.
// The session is preserved in the database; call Connect to resume.
func (c *Client) Disconnect() {
	c.inner.Disconnect()
}

// Logout removes this linked device from WhatsApp and closes the connection.
// The session in the database is invalidated. A new pairing is required
// before the next Connect.
func (c *Client) Logout() error {
	return c.inner.Logout(context.Background())
}

// Close disconnects and releases the database. Call when the Android service
// is stopping. After Close, the Client must not be used again.
func (c *Client) Close() error {
	c.inner.Disconnect()
	return c.db.Close()
}

// IsConnected reports whether the WebSocket is currently open.
func (c *Client) IsConnected() bool {
	return c.inner.IsConnected()
}

// IsLoggedIn reports whether a valid session exists (paired and not logged out).
func (c *Client) IsLoggedIn() bool {
	return c.inner.IsLoggedIn()
}

// GetJID returns the device JID assigned by WhatsApp (e.g. "6591234567:3@s.whatsapp.net"),
// or an empty string if the device is not yet paired.
func (c *Client) GetJID() string {
	id := c.inner.Store.ID
	if id == nil {
		return ""
	}
	return id.String()
}

// SendMessage sends text to chatJID (e.g. "6599887766@s.whatsapp.net" for
// a 1:1 chat, or "120363xxxxxxxx@g.us" for a group).
//
// The send is fire-and-forget: this method returns as soon as the message
// is dispatched. If the send fails, Listener.OnError is called with details.
func (c *Client) SendMessage(chatJID, text string) error {
	jid, err := types.ParseJID(chatJID)
	if err != nil {
		return fmt.Errorf("invalid JID %q: %w", chatJID, err)
	}
	go func() {
		msg := &waE2E.Message{Conversation: proto.String(text)}
		_, err := c.inner.SendMessage(context.Background(), jid, msg)
		if err != nil {
			c.emit(func(l Listener) {
				l.OnError(fmt.Sprintf("send to %s failed: %v", chatJID, err))
			})
		}
	}()
	return nil
}

// --- internal ---

func (c *Client) emit(fn func(Listener)) {
	c.mu.RLock()
	l := c.listener
	c.mu.RUnlock()
	if l != nil {
		fn(l)
	}
}

func (c *Client) handleEvent(evt interface{}) {
	switch v := evt.(type) {
	case *events.Connected:
		c.emit(func(l Listener) { l.OnConnected() })

	case *events.Disconnected:
		c.emit(func(l Listener) { l.OnDisconnected() })

	case *events.LoggedOut:
		c.emit(func(l Listener) { l.OnLoggedOut() })

	case *events.PairSuccess:
		c.emit(func(l Listener) { l.OnPaired(v.ID.String()) })

	case *events.ConnectFailure:
		c.emit(func(l Listener) {
			l.OnError(fmt.Sprintf("connect failure: %s", v.Reason))
		})

	case *events.TemporaryBan:
		c.emit(func(l Listener) {
			l.OnError(fmt.Sprintf("temporary ban: %s (expires %s)", v.Code, v.Expire))
		})

	case *events.ClientOutdated:
		c.emit(func(l Listener) { l.OnError("client outdated — update required") })

	case *events.Message:
		body, mediaType := extractBody(v.Message)
		ts := v.Info.Timestamp
		storeMessage(c.db, v.Info.ID, v.Info.Chat.String(),
			v.Info.Sender.String(), ts,
			body, mediaType, v.Info.IsFromMe, v.Info.IsGroup)
		msg := &Message{
			ID:        v.Info.ID,
			ChatJID:   v.Info.Chat.String(),
			SenderJID: v.Info.Sender.String(),
			Body:      body,
			MediaType: mediaType,
			Timestamp: ts.Unix(),
			FromMe:    v.Info.IsFromMe,
			IsGroup:   v.Info.IsGroup,
		}
		c.emit(func(l Listener) { l.OnMessage(msg) })

	case *events.HistorySync:
		stored := 0
		for _, conv := range v.Data.GetConversations() {
			chatJID := conv.GetID()
			isGroup := strings.HasSuffix(chatJID, "@g.us")
			for _, m := range conv.GetMessages() {
				info := m.GetMessage()
				if info == nil {
					continue
				}
				body, mediaType := extractBody(info.GetMessage())
				ts := time.Unix(int64(info.GetMessageTimestamp()), 0)
				sender := info.GetKey().GetParticipant()
				if sender == "" {
					sender = info.GetKey().GetRemoteJID()
				}
				if storeMessage(c.db,
					info.GetKey().GetID(), chatJID, sender, ts,
					body, mediaType,
					info.GetKey().GetFromMe(), isGroup) {
					stored++
				}
			}
		}
		c.emit(func(l Listener) { l.OnHistorySync(stored) })
	}
}
