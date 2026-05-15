package whatsbot

import (
	"database/sql"
	"time"
)

func initMessageTable(db *sql.DB) error {
	_, err := db.Exec(`
		CREATE TABLE IF NOT EXISTS wa_messages (
			id         TEXT PRIMARY KEY,
			chat_jid   TEXT NOT NULL,
			sender_jid TEXT NOT NULL,
			timestamp  INTEGER NOT NULL,
			body       TEXT,
			media_type TEXT,
			status     TEXT,
			push_name  TEXT,
			is_from_me INTEGER NOT NULL DEFAULT 0,
			is_group   INTEGER NOT NULL DEFAULT 0,
			is_deleted INTEGER NOT NULL DEFAULT 0
		);
		CREATE INDEX IF NOT EXISTS idx_wamsg_chat ON wa_messages(chat_jid, timestamp);

		CREATE TABLE IF NOT EXISTS wa_chats (
			jid                  TEXT PRIMARY KEY,
			name                 TEXT,
			description          TEXT,
			is_group             INTEGER NOT NULL DEFAULT 0,
			archived             INTEGER NOT NULL DEFAULT 0,
			pinned               INTEGER NOT NULL DEFAULT 0,
			last_msg_ts          INTEGER,
			ephemeral_expiration INTEGER NOT NULL DEFAULT 0,
			created_at           INTEGER,
			created_by           TEXT
		);

		CREATE TABLE IF NOT EXISTS wa_group_members (
			chat_jid   TEXT NOT NULL,
			member_jid TEXT NOT NULL,
			rank       TEXT NOT NULL DEFAULT 'regular',
			PRIMARY KEY (chat_jid, member_jid)
		);

		CREATE TABLE IF NOT EXISTS wa_reactions (
			message_id  TEXT NOT NULL,
			chat_jid    TEXT NOT NULL,
			reactor_jid TEXT NOT NULL,
			emoji       TEXT NOT NULL,
			timestamp   INTEGER NOT NULL,
			PRIMARY KEY (message_id, reactor_jid)
		);
	`)
	if err != nil {
		return err
	}

	// Migrate existing databases that predate new columns.
	// ALTER TABLE ADD COLUMN errors are silently ignored — they mean the
	// column already exists, which is fine.
	migrations := []string{
		`ALTER TABLE wa_messages ADD COLUMN status TEXT`,
		`ALTER TABLE wa_messages ADD COLUMN push_name TEXT`,
		`ALTER TABLE wa_messages ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0`,
		`ALTER TABLE wa_chats ADD COLUMN description TEXT`,
		`ALTER TABLE wa_chats ADD COLUMN archived INTEGER NOT NULL DEFAULT 0`,
		`ALTER TABLE wa_chats ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0`,
		`ALTER TABLE wa_chats ADD COLUMN last_msg_ts INTEGER`,
		`ALTER TABLE wa_chats ADD COLUMN ephemeral_expiration INTEGER NOT NULL DEFAULT 0`,
		`ALTER TABLE wa_chats ADD COLUMN created_at INTEGER`,
		`ALTER TABLE wa_chats ADD COLUMN created_by TEXT`,
	}
	for _, m := range migrations {
		db.Exec(m)
	}
	return nil
}

// storeMessage inserts a message. Returns true if inserted, false if it already
// existed (INSERT OR IGNORE) or the insert failed.
func storeMessage(db *sql.DB,
	id, chatJID, senderJID string, ts time.Time,
	body, mediaType, status, pushName string,
	isFromMe, isGroup, isDeleted bool) bool {

	fromMe, grp, del := 0, 0, 0
	if isFromMe {
		fromMe = 1
	}
	if isGroup {
		grp = 1
	}
	if isDeleted {
		del = 1
	}
	_, err := db.Exec(`
		INSERT OR IGNORE INTO wa_messages
			(id, chat_jid, sender_jid, timestamp, body, media_type,
			 status, push_name, is_from_me, is_group, is_deleted)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		id, chatJID, senderJID, ts.Unix(),
		body, mediaType, status, pushName,
		fromMe, grp, del)
	return err == nil
}

func markMessageDeleted(db *sql.DB, messageID string) {
	db.Exec(`UPDATE wa_messages SET is_deleted = 1 WHERE id = ?`, messageID)
}

func upsertChat(db *sql.DB,
	jid, name, description string,
	isGroup, archived, pinned bool,
	lastMsgTS, createdAt uint64,
	createdBy string,
	ephemeralExpiration uint32) {

	grp, arch, pin := 0, 0, 0
	if isGroup {
		grp = 1
	}
	if archived {
		arch = 1
	}
	if pinned {
		pin = 1
	}
	db.Exec(`
		INSERT INTO wa_chats
			(jid, name, description, is_group, archived, pinned,
			 last_msg_ts, ephemeral_expiration, created_at, created_by)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
		ON CONFLICT(jid) DO UPDATE SET
			name                 = excluded.name,
			description          = excluded.description,
			archived             = excluded.archived,
			pinned               = excluded.pinned,
			last_msg_ts          = excluded.last_msg_ts,
			ephemeral_expiration = excluded.ephemeral_expiration,
			created_at           = excluded.created_at,
			created_by           = excluded.created_by`,
		jid, name, description, grp, arch, pin,
		lastMsgTS, ephemeralExpiration, createdAt, createdBy)
}

func upsertGroupMember(db *sql.DB, chatJID, memberJID, rank string) {
	db.Exec(`
		INSERT INTO wa_group_members (chat_jid, member_jid, rank) VALUES (?, ?, ?)
		ON CONFLICT(chat_jid, member_jid) DO UPDATE SET rank = excluded.rank`,
		chatJID, memberJID, rank)
}

// storeReaction inserts or updates a reaction. An empty emoji means the
// reaction was removed — the row is deleted in that case.
func storeReaction(db *sql.DB, messageID, chatJID, reactorJID, emoji string, timestampMS int64) {
	if emoji == "" {
		db.Exec(`DELETE FROM wa_reactions WHERE message_id = ? AND reactor_jid = ?`,
			messageID, reactorJID)
		return
	}
	db.Exec(`
		INSERT INTO wa_reactions (message_id, chat_jid, reactor_jid, emoji, timestamp)
		VALUES (?, ?, ?, ?, ?)
		ON CONFLICT(message_id, reactor_jid) DO UPDATE SET
			emoji     = excluded.emoji,
			timestamp = excluded.timestamp`,
		messageID, chatJID, reactorJID, emoji, timestampMS/1000)
}
