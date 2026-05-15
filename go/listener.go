package whatsbot

// Listener receives events from the WhatsApp client.
// Implement this interface in Kotlin and pass it to Client.SetListener.
//
// Methods are called from a goroutine owned by the client — if you need
// to update Android UI, post to the main thread inside each method.
// Do not block inside a method: doing so stalls the client event queue.
type Listener interface {
	// OnConnected fires when the WebSocket is open and authenticated.
	OnConnected()

	// OnDisconnected fires when the WebSocket closes for any reason.
	OnDisconnected()

	// OnPairingCode delivers the 8-character code the user enters in
	// WhatsApp → Linked Devices → Link with Phone Number.
	// Only fires on first connect when no session exists.
	OnPairingCode(code string)

	// OnPaired fires when the pairing handshake completes successfully.
	// jid is the device JID assigned by WhatsApp.
	OnPaired(jid string)

	// OnMessage fires for every live inbound or outbound message.
	OnMessage(msg *Message)

	// OnHistorySync fires after each history sync batch.
	// stored is the number of messages written to the database in this batch.
	OnHistorySync(stored int)

	// OnLoggedOut fires when WhatsApp removes this linked device.
	// The session is invalid after this event; call Close and re-pair.
	OnLoggedOut()

	// OnError fires for asynchronous failures (e.g. send errors, bans).
	// Synchronous errors from Connect, SendMessage, etc. are returned
	// as Go errors which gomobile surfaces as Java exceptions.
	OnError(msg string)
}
