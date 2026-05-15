package whatsbot

import (
	"testing"

	"go.mau.fi/whatsmeow/proto/waE2E"
	"google.golang.org/protobuf/proto"
)

func TestExtractBodyNil(t *testing.T) {
	body, mediaType := extractBody(nil)
	if body != "" || mediaType != "" {
		t.Errorf("nil: got %q/%q, want empty", body, mediaType)
	}
}

func TestExtractBodyConversation(t *testing.T) {
	msg := &waE2E.Message{Conversation: proto.String("hello")}
	body, mediaType := extractBody(msg)
	if body != "hello" || mediaType != "" {
		t.Errorf("got %q/%q", body, mediaType)
	}
}

func TestExtractBodyExtendedText(t *testing.T) {
	msg := &waE2E.Message{
		ExtendedTextMessage: &waE2E.ExtendedTextMessage{Text: proto.String("link text")},
	}
	body, mediaType := extractBody(msg)
	if body != "link text" || mediaType != "" {
		t.Errorf("got %q/%q", body, mediaType)
	}
}

func TestExtractBodyImage(t *testing.T) {
	msg := &waE2E.Message{
		ImageMessage: &waE2E.ImageMessage{Caption: proto.String("my photo")},
	}
	body, mediaType := extractBody(msg)
	if body != "my photo" || mediaType != "image" {
		t.Errorf("got %q/%q", body, mediaType)
	}
}

func TestExtractBodyVideo(t *testing.T) {
	msg := &waE2E.Message{
		VideoMessage: &waE2E.VideoMessage{Caption: proto.String("my video")},
	}
	body, mediaType := extractBody(msg)
	if body != "my video" || mediaType != "video" {
		t.Errorf("got %q/%q", body, mediaType)
	}
}

func TestExtractBodyAudio(t *testing.T) {
	msg := &waE2E.Message{AudioMessage: &waE2E.AudioMessage{}}
	body, mediaType := extractBody(msg)
	if body != "" || mediaType != "audio" {
		t.Errorf("got %q/%q", body, mediaType)
	}
}

func TestExtractBodyDocument(t *testing.T) {
	msg := &waE2E.Message{
		DocumentMessage: &waE2E.DocumentMessage{FileName: proto.String("report.pdf")},
	}
	body, mediaType := extractBody(msg)
	if body != "report.pdf" || mediaType != "document" {
		t.Errorf("got %q/%q", body, mediaType)
	}
}

func TestExtractBodySticker(t *testing.T) {
	msg := &waE2E.Message{StickerMessage: &waE2E.StickerMessage{}}
	body, mediaType := extractBody(msg)
	if body != "" || mediaType != "sticker" {
		t.Errorf("got %q/%q", body, mediaType)
	}
}

func TestExtractBodyLocation(t *testing.T) {
	lat := float64(1.3521)
	lon := float64(103.8198)
	msg := &waE2E.Message{
		LocationMessage: &waE2E.LocationMessage{
			DegreesLatitude:  &lat,
			DegreesLongitude: &lon,
		},
	}
	body, mediaType := extractBody(msg)
	if body != "1.352100,103.819800" || mediaType != "location" {
		t.Errorf("got %q/%q", body, mediaType)
	}
}

func TestExtractBodyUnknown(t *testing.T) {
	msg := &waE2E.Message{}
	body, mediaType := extractBody(msg)
	if body != "" || mediaType != "other" {
		t.Errorf("empty message: got %q/%q, want \"\"/\"other\"", body, mediaType)
	}
}
