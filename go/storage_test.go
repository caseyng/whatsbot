package whatsbot

import (
	"database/sql"
	"testing"
	"time"

	_ "github.com/mattn/go-sqlite3"
)

func newTestDB(t *testing.T) *sql.DB {
	t.Helper()
	db, err := sql.Open("sqlite3", ":memory:?_foreign_keys=on")
	if err != nil {
		t.Fatalf("open: %v", err)
	}
	if err := initMessageTable(db); err != nil {
		t.Fatalf("initMessageTable: %v", err)
	}
	t.Cleanup(func() { db.Close() })
	return db
}

func TestInitMessageTableIdempotent(t *testing.T) {
	db := newTestDB(t)
	// Second call must not fail (CREATE TABLE IF NOT EXISTS + ignored ALTERs).
	if err := initMessageTable(db); err != nil {
		t.Fatalf("second initMessageTable: %v", err)
	}
}

func TestInitMessageTableSchema(t *testing.T) {
	db := newTestDB(t)
	tables := []string{"wa_messages", "wa_chats", "wa_group_members", "wa_reactions"}
	for _, tbl := range tables {
		var name string
		err := db.QueryRow(
			`SELECT name FROM sqlite_master WHERE type='table' AND name=?`, tbl,
		).Scan(&name)
		if err != nil || name != tbl {
			t.Errorf("table %q not found: %v", tbl, err)
		}
	}
}

func TestStoreMessageInsertsOnce(t *testing.T) {
	db := newTestDB(t)
	ts := time.Unix(1_700_000_000, 0)

	ok := storeMessage(db, "msg1", "chat@s.whatsapp.net", "sender@s.whatsapp.net",
		ts, "hello", "", "READ", "Alice", false, false, false)
	if !ok {
		t.Fatal("first insert returned false")
	}

	// Duplicate — INSERT OR IGNORE, must not error and return true (no actual insert but no error).
	ok2 := storeMessage(db, "msg1", "chat@s.whatsapp.net", "sender@s.whatsapp.net",
		ts, "hello", "", "READ", "Alice", false, false, false)
	if !ok2 {
		t.Fatal("duplicate insert returned false (expected true since no DB error)")
	}

	var count int
	db.QueryRow(`SELECT COUNT(*) FROM wa_messages`).Scan(&count)
	if count != 1 {
		t.Errorf("expected 1 row, got %d", count)
	}
}

func TestStoreMessageFields(t *testing.T) {
	db := newTestDB(t)
	ts := time.Unix(1_700_000_001, 0)

	storeMessage(db, "msg2", "group@g.us", "member@s.whatsapp.net",
		ts, "caption", "image", "DELIVERY_ACK", "Bob", false, true, true)

	var body, mediaType, status, pushName string
	var isFromMe, isGroup, isDeleted int
	err := db.QueryRow(`
		SELECT body, media_type, status, push_name, is_from_me, is_group, is_deleted
		FROM wa_messages WHERE id = 'msg2'`).
		Scan(&body, &mediaType, &status, &pushName, &isFromMe, &isGroup, &isDeleted)
	if err != nil {
		t.Fatalf("query: %v", err)
	}
	if body != "caption" || mediaType != "image" || status != "DELIVERY_ACK" ||
		pushName != "Bob" || isFromMe != 0 || isGroup != 1 || isDeleted != 1 {
		t.Errorf("unexpected values: body=%q media=%q status=%q push=%q fromMe=%d group=%d deleted=%d",
			body, mediaType, status, pushName, isFromMe, isGroup, isDeleted)
	}
}

func TestMarkMessageDeleted(t *testing.T) {
	db := newTestDB(t)
	storeMessage(db, "msg3", "chat@s.whatsapp.net", "s@s.whatsapp.net",
		time.Now(), "hi", "", "", "", false, false, false)

	markMessageDeleted(db, "msg3")

	var isDeleted int
	db.QueryRow(`SELECT is_deleted FROM wa_messages WHERE id = 'msg3'`).Scan(&isDeleted)
	if isDeleted != 1 {
		t.Errorf("expected is_deleted=1, got %d", isDeleted)
	}
}

func TestUpsertChatInsertAndUpdate(t *testing.T) {
	db := newTestDB(t)

	upsertChat(db, "g@g.us", "My Group", "A group", true, false, false, 1000, 900, "owner@s.whatsapp.net", 0)
	var name, description string
	var isGroup, archived, pinned int
	var lastMsgTS, createdAt uint64
	var createdBy string
	var ephExp uint32
	db.QueryRow(`SELECT name, description, is_group, archived, pinned, last_msg_ts, created_at, created_by, ephemeral_expiration
		FROM wa_chats WHERE jid = 'g@g.us'`).
		Scan(&name, &description, &isGroup, &archived, &pinned, &lastMsgTS, &createdAt, &createdBy, &ephExp)

	if name != "My Group" || description != "A group" || isGroup != 1 ||
		archived != 0 || pinned != 0 || lastMsgTS != 1000 || createdAt != 900 ||
		createdBy != "owner@s.whatsapp.net" || ephExp != 0 {
		t.Errorf("unexpected insert values: name=%q desc=%q group=%d arch=%d pin=%d ts=%d ca=%d cb=%q eph=%d",
			name, description, isGroup, archived, pinned, lastMsgTS, createdAt, createdBy, ephExp)
	}

	// Update: rename, archive, pin.
	upsertChat(db, "g@g.us", "Renamed", "Updated desc", true, true, true, 2000, 900, "owner@s.whatsapp.net", 86400)
	db.QueryRow(`SELECT name, description, archived, pinned, last_msg_ts, ephemeral_expiration
		FROM wa_chats WHERE jid = 'g@g.us'`).
		Scan(&name, &description, &archived, &pinned, &lastMsgTS, &ephExp)
	if name != "Renamed" || description != "Updated desc" || archived != 1 || pinned != 1 || lastMsgTS != 2000 || ephExp != 86400 {
		t.Errorf("unexpected update values: name=%q desc=%q arch=%d pin=%d ts=%d eph=%d",
			name, description, archived, pinned, lastMsgTS, ephExp)
	}
}

func TestUpsertGroupMember(t *testing.T) {
	db := newTestDB(t)

	upsertGroupMember(db, "g@g.us", "alice@s.whatsapp.net", "REGULAR")
	upsertGroupMember(db, "g@g.us", "alice@s.whatsapp.net", "ADMIN")

	var rank string
	db.QueryRow(`SELECT rank FROM wa_group_members WHERE chat_jid='g@g.us' AND member_jid='alice@s.whatsapp.net'`).Scan(&rank)
	if rank != "ADMIN" {
		t.Errorf("expected ADMIN, got %q", rank)
	}
}

func TestStoreReactionInsertAndUpdate(t *testing.T) {
	db := newTestDB(t)

	storeReaction(db, "msg1", "chat@g.us", "alice@s.whatsapp.net", "👍", 1_700_000_000_000)
	var emoji string
	var ts int64
	db.QueryRow(`SELECT emoji, timestamp FROM wa_reactions WHERE message_id='msg1' AND reactor_jid='alice@s.whatsapp.net'`).
		Scan(&emoji, &ts)
	if emoji != "👍" {
		t.Errorf("expected 👍, got %q", emoji)
	}
	if ts != 1_700_000_000 {
		t.Errorf("expected timestamp 1700000000, got %d", ts)
	}

	// Change emoji.
	storeReaction(db, "msg1", "chat@g.us", "alice@s.whatsapp.net", "❤️", 1_700_000_001_000)
	db.QueryRow(`SELECT emoji FROM wa_reactions WHERE message_id='msg1' AND reactor_jid='alice@s.whatsapp.net'`).Scan(&emoji)
	if emoji != "❤️" {
		t.Errorf("expected ❤️, got %q", emoji)
	}
}

func TestStoreReactionUnreaction(t *testing.T) {
	db := newTestDB(t)

	storeReaction(db, "msg1", "chat@g.us", "bob@s.whatsapp.net", "😂", 1_700_000_000_000)
	// Empty emoji = unreaction → row must be deleted.
	storeReaction(db, "msg1", "chat@g.us", "bob@s.whatsapp.net", "", 1_700_000_001_000)

	var count int
	db.QueryRow(`SELECT COUNT(*) FROM wa_reactions WHERE message_id='msg1' AND reactor_jid='bob@s.whatsapp.net'`).Scan(&count)
	if count != 0 {
		t.Errorf("expected row deleted on unreaction, got count=%d", count)
	}
}

func TestStoreReactionMultipleReactors(t *testing.T) {
	db := newTestDB(t)

	storeReaction(db, "msg1", "chat@g.us", "a@s.whatsapp.net", "👍", 1_000_000_000)
	storeReaction(db, "msg1", "chat@g.us", "b@s.whatsapp.net", "❤️", 1_000_000_001)
	storeReaction(db, "msg1", "chat@g.us", "c@s.whatsapp.net", "😂", 1_000_000_002)
	// Remove b's reaction.
	storeReaction(db, "msg1", "chat@g.us", "b@s.whatsapp.net", "", 1_000_000_003)

	var count int
	db.QueryRow(`SELECT COUNT(*) FROM wa_reactions WHERE message_id='msg1'`).Scan(&count)
	if count != 2 {
		t.Errorf("expected 2 reactions remaining, got %d", count)
	}
}
