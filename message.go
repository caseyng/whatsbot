package whatsbot

import (
	"fmt"

	"go.mau.fi/whatsmeow/proto/waE2E"
)

// Message carries the fields of a single WhatsApp message.
// Delivered via Listener.OnMessage for live messages.
// Also written to the wa_messages table in the SQLite database.
type Message struct {
	ID        string
	ChatJID   string
	SenderJID string
	Body      string
	MediaType string // "", "image", "video", "audio", "document", "sticker", "location", "other"
	Timestamp int64  // Unix seconds
	FromMe    bool
	IsGroup   bool
}

func extractBody(msg *waE2E.Message) (body, mediaType string) {
	if msg == nil {
		return "", ""
	}
	switch {
	case msg.GetConversation() != "":
		return msg.GetConversation(), ""
	case msg.GetExtendedTextMessage() != nil:
		return msg.GetExtendedTextMessage().GetText(), ""
	case msg.GetImageMessage() != nil:
		return msg.GetImageMessage().GetCaption(), "image"
	case msg.GetVideoMessage() != nil:
		return msg.GetVideoMessage().GetCaption(), "video"
	case msg.GetAudioMessage() != nil:
		return "", "audio"
	case msg.GetDocumentMessage() != nil:
		return msg.GetDocumentMessage().GetFileName(), "document"
	case msg.GetStickerMessage() != nil:
		return "", "sticker"
	case msg.GetLocationMessage() != nil:
		return fmt.Sprintf("%.6f,%.6f",
			msg.GetLocationMessage().GetDegreesLatitude(),
			msg.GetLocationMessage().GetDegreesLongitude()), "location"
	default:
		return "", "other"
	}
}
