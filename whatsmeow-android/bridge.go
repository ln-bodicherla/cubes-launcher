package whatsmeowbridge

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	_ "github.com/mattn/go-sqlite3" // register sqlite3 driver
	_ "golang.org/x/mobile/bind"    // required by gomobile

	"go.mau.fi/whatsmeow"
	"go.mau.fi/whatsmeow/proto/waCompanionReg"
	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/store"
	"go.mau.fi/whatsmeow/store/sqlstore"
	"go.mau.fi/whatsmeow/types"
	"go.mau.fi/whatsmeow/types/events"
	waLog "go.mau.fi/whatsmeow/util/log"
	"google.golang.org/protobuf/proto"
)

// EventHandler is implemented in Kotlin to receive callbacks from Go
type EventHandler interface {
	OnMessage(jsonPayload string)
	OnQRCode(qrCode string)
	OnConnected()
	OnDisconnected()
	OnHistorySync(jsonPayload string)
	OnDebug(msg string)
	// Phase 2: Message interactions
	OnReceipt(jsonPayload string)
	OnReaction(jsonPayload string)
	OnMessageRevoke(jsonPayload string)
	OnMessageEdit(jsonPayload string)
	// Phase 3: Groups
	OnGroupInfoUpdate(jsonPayload string)
	// Phase 4: Presence
	OnPresence(jsonPayload string)
	OnChatPresence(jsonPayload string)
	// Phase 7: Calls
	OnCallOffer(jsonPayload string)
}

var (
	client             *whatsmeow.Client
	container          *sqlstore.Container
	eventHandler       EventHandler
	mu                 sync.Mutex
	dbPath             string
	mediaPath          string
	phonePairingActive bool
)

// Init initializes the whatsmeow client with the given SQLite database path
func Init(path string) {
	mu.Lock()
	defer mu.Unlock()

	dbPath = path
	store.DeviceProps.Os = proto.String("Cubes Launcher")
	store.DeviceProps.PlatformType = waCompanionReg.DeviceProps_CHROME.Enum()
}

// SetMediaPath sets the directory where downloaded media files are stored
func SetMediaPath(path string) {
	mu.Lock()
	defer mu.Unlock()

	mediaPath = path
	os.MkdirAll(path, 0755)
}

func getContainer() (*sqlstore.Container, error) {
	if container != nil {
		return container, nil
	}
	dbLog := waLog.Noop
	var err error
	container, err = sqlstore.New(context.Background(), "sqlite3", fmt.Sprintf("file:%s?_foreign_keys=on", dbPath), dbLog)
	if err != nil {
		return nil, fmt.Errorf("failed to create store: %w", err)
	}
	return container, nil
}

func getOrCreateClient() (*whatsmeow.Client, error) {
	if client != nil {
		return client, nil
	}

	cont, err := getContainer()
	if err != nil {
		return nil, err
	}

	deviceStore, err := cont.GetFirstDevice(context.Background())
	if err != nil {
		return nil, fmt.Errorf("failed to get device: %w", err)
	}

	clientLog := waLog.Noop
	client = whatsmeow.NewClient(deviceStore, clientLog)
	client.AddEventHandler(handleEvent)
	return client, nil
}

// SetEventHandler sets the callback handler (implemented in Kotlin)
func SetEventHandler(handler EventHandler) {
	mu.Lock()
	defer mu.Unlock()
	eventHandler = handler
}

// GetQRChannel starts the QR pairing flow and returns the first QR code string.
// Subsequent QR codes are delivered via OnQRCode callback.
func GetQRChannel() string {
	mu.Lock()
	defer mu.Unlock()

	cli, err := getOrCreateClient()
	if err != nil {
		return ""
	}

	qrChan, _ := cli.GetQRChannel(context.Background())

	go func() {
		for evt := range qrChan {
			if evt.Event == "code" {
				if eventHandler != nil {
					eventHandler.OnQRCode(evt.Code)
				}
			}
		}
	}()

	err = cli.Connect()
	if err != nil {
		return ""
	}

	return ""
}

// PairPhone starts phone-number based pairing and returns an 8-digit linking code.
// The phone number must include the country code (e.g. "+14155552671").
func PairPhone(phone string) string {
	mu.Lock()
	defer mu.Unlock()

	// Disconnect any existing connection from QR flow
	if client != nil && client.IsConnected() {
		client.Disconnect()
	}
	client = nil

	cli, err := getOrCreateClient()
	if err != nil {
		return "ERROR:" + err.Error()
	}

	// Must unlock before blocking operations to avoid deadlock with event handler
	mu.Unlock()

	// Get QR channel — PairPhone requires the connection to be established first
	qrChan, _ := cli.GetQRChannel(context.Background())

	err = cli.Connect()
	if err != nil {
		mu.Lock() // re-lock for deferred unlock
		return "ERROR:" + err.Error()
	}

	// Wait for first QR event or timeout (confirms connection is established)
	select {
	case <-qrChan:
	case <-time.After(5 * time.Second):
	}

	mu.Lock() // re-lock for deferred unlock

	// Strip everything except digits from the phone number
	cleaned := strings.Map(func(r rune) rune {
		if r >= '0' && r <= '9' {
			return r
		}
		return -1
	}, phone)

	// clientDisplayName must be "Browser (OS)" format — WhatsApp validates this
	code, err := cli.PairPhone(context.Background(), cleaned, true, whatsmeow.PairClientChrome, "Chrome (Linux)")
	if err != nil {
		return "ERROR:" + err.Error()
	}

	phonePairingActive = true
	return code
}

// Connect connects to WhatsApp (for already-paired sessions)
func Connect() error {
	mu.Lock()
	defer mu.Unlock()

	cli, err := getOrCreateClient()
	if err != nil {
		return err
	}

	return cli.Connect()
}

// Disconnect disconnects from WhatsApp
func Disconnect() {
	mu.Lock()
	defer mu.Unlock()

	if client != nil {
		client.Disconnect()
	}
}

// IsConnected returns whether the client is currently connected
func IsConnected() bool {
	mu.Lock()
	defer mu.Unlock()

	if client == nil {
		return false
	}
	return client.IsConnected()
}

// IsLoggedIn returns whether there is a valid session stored
func IsLoggedIn() bool {
	mu.Lock()
	defer mu.Unlock()

	if client == nil {
		cont, err := getContainer()
		if err != nil {
			return false
		}
		devices, err := cont.GetAllDevices(context.Background())
		if err != nil {
			return false
		}
		return len(devices) > 0
	}
	return client.Store.ID != nil
}

// SendMessage sends a text message to the given JID
func SendMessage(jid string, text string) error {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return fmt.Errorf("not connected")
	}

	parsedJID, err := types.ParseJID(jid)
	if err != nil {
		return fmt.Errorf("invalid JID: %w", err)
	}

	msg := &waE2E.Message{
		Conversation: proto.String(text),
	}

	_, err = cli.SendMessage(context.Background(), parsedJID, msg)
	return err
}

// --- Phase 1: Media Upload & Send ---

func randomFileName(ext string) string {
	b := make([]byte, 8)
	rand.Read(b)
	return hex.EncodeToString(b) + ext
}

// UploadAndSendImage uploads an image and sends it to the given JID.
// filePath is the local file path. caption and mimeType are optional (mimeType defaults to "image/jpeg").
func UploadAndSendImage(jid string, filePath string, caption string, mimeType string) error {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return fmt.Errorf("not connected")
	}

	parsedJID, err := types.ParseJID(jid)
	if err != nil {
		return fmt.Errorf("invalid JID: %w", err)
	}

	data, err := os.ReadFile(filePath)
	if err != nil {
		return fmt.Errorf("read file: %w", err)
	}

	if mimeType == "" {
		mimeType = "image/jpeg"
	}

	uploaded, err := cli.Upload(context.Background(), data, whatsmeow.MediaImage)
	if err != nil {
		return fmt.Errorf("upload: %w", err)
	}

	imgMsg := &waE2E.ImageMessage{
		URL:           proto.String(uploaded.URL),
		DirectPath:    proto.String(uploaded.DirectPath),
		MediaKey:      uploaded.MediaKey,
		FileEncSHA256: uploaded.FileEncSHA256,
		FileSHA256:    uploaded.FileSHA256,
		FileLength:    proto.Uint64(uint64(len(data))),
		Mimetype:      proto.String(mimeType),
	}
	if caption != "" {
		imgMsg.Caption = proto.String(caption)
	}

	msg := &waE2E.Message{ImageMessage: imgMsg}
	_, err = cli.SendMessage(context.Background(), parsedJID, msg)
	return err
}

// UploadAndSendVideo uploads a video and sends it to the given JID.
func UploadAndSendVideo(jid string, filePath string, caption string, mimeType string) error {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return fmt.Errorf("not connected")
	}

	parsedJID, err := types.ParseJID(jid)
	if err != nil {
		return fmt.Errorf("invalid JID: %w", err)
	}

	data, err := os.ReadFile(filePath)
	if err != nil {
		return fmt.Errorf("read file: %w", err)
	}

	if mimeType == "" {
		mimeType = "video/mp4"
	}

	uploaded, err := cli.Upload(context.Background(), data, whatsmeow.MediaVideo)
	if err != nil {
		return fmt.Errorf("upload: %w", err)
	}

	vidMsg := &waE2E.VideoMessage{
		URL:           proto.String(uploaded.URL),
		DirectPath:    proto.String(uploaded.DirectPath),
		MediaKey:      uploaded.MediaKey,
		FileEncSHA256: uploaded.FileEncSHA256,
		FileSHA256:    uploaded.FileSHA256,
		FileLength:    proto.Uint64(uint64(len(data))),
		Mimetype:      proto.String(mimeType),
	}
	if caption != "" {
		vidMsg.Caption = proto.String(caption)
	}

	msg := &waE2E.Message{VideoMessage: vidMsg}
	_, err = cli.SendMessage(context.Background(), parsedJID, msg)
	return err
}

// UploadAndSendAudio uploads an audio file and sends it to the given JID.
func UploadAndSendAudio(jid string, filePath string, mimeType string) error {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return fmt.Errorf("not connected")
	}

	parsedJID, err := types.ParseJID(jid)
	if err != nil {
		return fmt.Errorf("invalid JID: %w", err)
	}

	data, err := os.ReadFile(filePath)
	if err != nil {
		return fmt.Errorf("read file: %w", err)
	}

	if mimeType == "" {
		mimeType = "audio/ogg; codecs=opus"
	}

	uploaded, err := cli.Upload(context.Background(), data, whatsmeow.MediaAudio)
	if err != nil {
		return fmt.Errorf("upload: %w", err)
	}

	audioMsg := &waE2E.AudioMessage{
		URL:           proto.String(uploaded.URL),
		DirectPath:    proto.String(uploaded.DirectPath),
		MediaKey:      uploaded.MediaKey,
		FileEncSHA256: uploaded.FileEncSHA256,
		FileSHA256:    uploaded.FileSHA256,
		FileLength:    proto.Uint64(uint64(len(data))),
		Mimetype:      proto.String(mimeType),
	}

	msg := &waE2E.Message{AudioMessage: audioMsg}
	_, err = cli.SendMessage(context.Background(), parsedJID, msg)
	return err
}

// UploadAndSendAudioPTT uploads a voice note (push-to-talk) and sends it.
func UploadAndSendAudioPTT(jid string, filePath string, mimeType string) error {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return fmt.Errorf("not connected")
	}

	parsedJID, err := types.ParseJID(jid)
	if err != nil {
		return fmt.Errorf("invalid JID: %w", err)
	}

	data, err := os.ReadFile(filePath)
	if err != nil {
		return fmt.Errorf("read file: %w", err)
	}

	if mimeType == "" {
		mimeType = "audio/ogg; codecs=opus"
	}

	uploaded, err := cli.Upload(context.Background(), data, whatsmeow.MediaAudio)
	if err != nil {
		return fmt.Errorf("upload: %w", err)
	}

	audioMsg := &waE2E.AudioMessage{
		URL:           proto.String(uploaded.URL),
		DirectPath:    proto.String(uploaded.DirectPath),
		MediaKey:      uploaded.MediaKey,
		FileEncSHA256: uploaded.FileEncSHA256,
		FileSHA256:    uploaded.FileSHA256,
		FileLength:    proto.Uint64(uint64(len(data))),
		Mimetype:      proto.String(mimeType),
		PTT:           proto.Bool(true),
	}

	msg := &waE2E.Message{AudioMessage: audioMsg}
	_, err = cli.SendMessage(context.Background(), parsedJID, msg)
	return err
}

// UploadAndSendDocument uploads a document and sends it to the given JID.
func UploadAndSendDocument(jid string, filePath string, fileName string, mimeType string) error {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return fmt.Errorf("not connected")
	}

	parsedJID, err := types.ParseJID(jid)
	if err != nil {
		return fmt.Errorf("invalid JID: %w", err)
	}

	data, err := os.ReadFile(filePath)
	if err != nil {
		return fmt.Errorf("read file: %w", err)
	}

	if mimeType == "" {
		mimeType = "application/octet-stream"
	}
	if fileName == "" {
		fileName = filepath.Base(filePath)
	}

	uploaded, err := cli.Upload(context.Background(), data, whatsmeow.MediaDocument)
	if err != nil {
		return fmt.Errorf("upload: %w", err)
	}

	docMsg := &waE2E.DocumentMessage{
		URL:           proto.String(uploaded.URL),
		DirectPath:    proto.String(uploaded.DirectPath),
		MediaKey:      uploaded.MediaKey,
		FileEncSHA256: uploaded.FileEncSHA256,
		FileSHA256:    uploaded.FileSHA256,
		FileLength:    proto.Uint64(uint64(len(data))),
		Mimetype:      proto.String(mimeType),
		FileName:      proto.String(fileName),
	}

	msg := &waE2E.Message{DocumentMessage: docMsg}
	_, err = cli.SendMessage(context.Background(), parsedJID, msg)
	return err
}

// DownloadMedia downloads media from a message. messageInfoJSON should contain
// the media message proto fields. Returns the local file path on success.
func DownloadMedia(messageInfoJSON string) string {
	mu.Lock()
	cli := client
	mp := mediaPath
	mu.Unlock()

	if cli == nil || mp == "" {
		return ""
	}

	var info struct {
		MessageType string `json:"messageType"`
		URL         string `json:"url"`
		DirectPath  string `json:"directPath"`
		MediaKey    []byte `json:"mediaKey"`
		FileEncSHA256 []byte `json:"fileEncSHA256"`
		FileSHA256  []byte `json:"fileSHA256"`
		FileLength  uint64 `json:"fileLength"`
		Mimetype    string `json:"mimetype"`
	}
	if err := json.Unmarshal([]byte(messageInfoJSON), &info); err != nil {
		return ""
	}

	var downloadable whatsmeow.DownloadableMessage
	switch info.MessageType {
	case "image":
		downloadable = &waE2E.ImageMessage{
			URL:           proto.String(info.URL),
			DirectPath:    proto.String(info.DirectPath),
			MediaKey:      info.MediaKey,
			FileEncSHA256: info.FileEncSHA256,
			FileSHA256:    info.FileSHA256,
			FileLength:    proto.Uint64(info.FileLength),
			Mimetype:      proto.String(info.Mimetype),
		}
	case "video":
		downloadable = &waE2E.VideoMessage{
			URL:           proto.String(info.URL),
			DirectPath:    proto.String(info.DirectPath),
			MediaKey:      info.MediaKey,
			FileEncSHA256: info.FileEncSHA256,
			FileSHA256:    info.FileSHA256,
			FileLength:    proto.Uint64(info.FileLength),
			Mimetype:      proto.String(info.Mimetype),
		}
	case "audio":
		downloadable = &waE2E.AudioMessage{
			URL:           proto.String(info.URL),
			DirectPath:    proto.String(info.DirectPath),
			MediaKey:      info.MediaKey,
			FileEncSHA256: info.FileEncSHA256,
			FileSHA256:    info.FileSHA256,
			FileLength:    proto.Uint64(info.FileLength),
			Mimetype:      proto.String(info.Mimetype),
		}
	case "document":
		downloadable = &waE2E.DocumentMessage{
			URL:           proto.String(info.URL),
			DirectPath:    proto.String(info.DirectPath),
			MediaKey:      info.MediaKey,
			FileEncSHA256: info.FileEncSHA256,
			FileSHA256:    info.FileSHA256,
			FileLength:    proto.Uint64(info.FileLength),
			Mimetype:      proto.String(info.Mimetype),
		}
	default:
		return ""
	}

	data, err := cli.Download(context.Background(), downloadable)
	if err != nil {
		return ""
	}

	ext := ".bin"
	switch {
	case strings.HasPrefix(info.Mimetype, "image/jpeg"):
		ext = ".jpg"
	case strings.HasPrefix(info.Mimetype, "image/png"):
		ext = ".png"
	case strings.HasPrefix(info.Mimetype, "image/webp"):
		ext = ".webp"
	case strings.HasPrefix(info.Mimetype, "video/mp4"):
		ext = ".mp4"
	case strings.HasPrefix(info.Mimetype, "audio/ogg"):
		ext = ".ogg"
	case strings.HasPrefix(info.Mimetype, "audio/mpeg"):
		ext = ".mp3"
	case strings.HasPrefix(info.Mimetype, "application/pdf"):
		ext = ".pdf"
	}

	localPath := filepath.Join(mp, randomFileName(ext))
	if err := os.WriteFile(localPath, data, 0644); err != nil {
		return ""
	}

	return localPath
}

// --- Phase 2: Message Interactions ---

// SendReaction sends an emoji reaction to a message.
func SendReaction(chatJID string, targetMessageID string, senderJID string, emoji string) error {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return fmt.Errorf("not connected")
	}

	parsedChat, err := types.ParseJID(chatJID)
	if err != nil {
		return fmt.Errorf("invalid chat JID: %w", err)
	}

	parsedSender, err := types.ParseJID(senderJID)
	if err != nil {
		return fmt.Errorf("invalid sender JID: %w", err)
	}

	msg := cli.BuildReaction(parsedChat, parsedSender, targetMessageID, emoji)
	_, err = cli.SendMessage(context.Background(), parsedChat, msg)
	return err
}

// EditMessage edits a previously sent message.
func EditMessage(chatJID string, targetMessageID string, newText string) error {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return fmt.Errorf("not connected")
	}

	parsedChat, err := types.ParseJID(chatJID)
	if err != nil {
		return fmt.Errorf("invalid JID: %w", err)
	}

	msg := cli.BuildEdit(parsedChat, targetMessageID, &waE2E.Message{
		Conversation: proto.String(newText),
	})

	_, err = cli.SendMessage(context.Background(), parsedChat, msg)
	return err
}

// RevokeMessage revokes (deletes for everyone) a message.
func RevokeMessage(chatJID string, senderJID string, targetMessageID string) error {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return fmt.Errorf("not connected")
	}

	parsedChat, err := types.ParseJID(chatJID)
	if err != nil {
		return fmt.Errorf("invalid chat JID: %w", err)
	}

	parsedSender, err := types.ParseJID(senderJID)
	if err != nil {
		return fmt.Errorf("invalid sender JID: %w", err)
	}

	msg := cli.BuildRevoke(parsedChat, parsedSender, targetMessageID)
	_, err = cli.SendMessage(context.Background(), parsedChat, msg)
	return err
}

// MarkRead marks messages as read.
func MarkRead(chatJID string, messageIDsJSON string) error {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return fmt.Errorf("not connected")
	}

	parsedChat, err := types.ParseJID(chatJID)
	if err != nil {
		return fmt.Errorf("invalid JID: %w", err)
	}

	var messageIDs []types.MessageID
	if err := json.Unmarshal([]byte(messageIDsJSON), &messageIDs); err != nil {
		return fmt.Errorf("invalid message IDs: %w", err)
	}

	return cli.MarkRead(context.Background(), messageIDs, time.Now(), parsedChat, types.EmptyJID)
}

// SendReplyMessage sends a text reply to a quoted message.
func SendReplyMessage(chatJID string, quotedID string, quotedSenderJID string, text string) error {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return fmt.Errorf("not connected")
	}

	parsedChat, err := types.ParseJID(chatJID)
	if err != nil {
		return fmt.Errorf("invalid JID: %w", err)
	}

	parsedQuotedSender, err := types.ParseJID(quotedSenderJID)
	if err != nil {
		return fmt.Errorf("invalid quoted sender JID: %w", err)
	}

	quotedSenderStr := parsedQuotedSender.String()
	msg := &waE2E.Message{
		ExtendedTextMessage: &waE2E.ExtendedTextMessage{
			Text: proto.String(text),
			ContextInfo: &waE2E.ContextInfo{
				StanzaID:    proto.String(quotedID),
				Participant: proto.String(quotedSenderStr),
			},
		},
	}

	_, err = cli.SendMessage(context.Background(), parsedChat, msg)
	return err
}

// --- Phase 3: Group Features ---

// GetGroupInfo returns JSON with group details (name, topic, participants).
func GetGroupInfo(jid string) string {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return "{}"
	}

	parsedJID, err := types.ParseJID(jid)
	if err != nil {
		return "{}"
	}

	info, err := cli.GetGroupInfo(context.Background(), parsedJID)
	if err != nil {
		return "{}"
	}

	type participant struct {
		JID          string `json:"jid"`
		IsAdmin      bool   `json:"isAdmin"`
		IsSuperAdmin bool   `json:"isSuperAdmin"`
	}

	var participants []participant
	for _, p := range info.Participants {
		participants = append(participants, participant{
			JID:          p.JID.String(),
			IsAdmin:      p.IsAdmin,
			IsSuperAdmin: p.IsSuperAdmin,
		})
	}

	result := struct {
		JID          string        `json:"jid"`
		Name         string        `json:"name"`
		Topic        string        `json:"topic"`
		Participants []participant `json:"participants"`
		CreatedAt    int64         `json:"createdAt"`
		IsLocked     bool          `json:"isLocked"`
		IsAnnounce   bool          `json:"isAnnounce"`
	}{
		JID:          info.JID.String(),
		Name:         info.Name,
		Topic:        info.Topic,
		Participants: participants,
		CreatedAt:    info.GroupCreated.Unix(),
		IsLocked:     info.IsLocked,
		IsAnnounce:   info.IsAnnounce,
	}

	data, _ := json.Marshal(result)
	return string(data)
}

// CreateGroup creates a new group with the given name and participants.
// participantJIDsJSON is a JSON array of JID strings. Returns the new group JID.
func CreateGroup(name string, participantJIDsJSON string) string {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return ""
	}

	var jidStrings []string
	if err := json.Unmarshal([]byte(participantJIDsJSON), &jidStrings); err != nil {
		return ""
	}

	var participants []types.JID
	for _, s := range jidStrings {
		j, err := types.ParseJID(s)
		if err == nil {
			participants = append(participants, j)
		}
	}

	req := whatsmeow.ReqCreateGroup{
		Name:         name,
		Participants: participants,
	}

	info, err := cli.CreateGroup(context.Background(), req)
	if err != nil {
		return ""
	}

	return info.JID.String()
}

// SetGroupName changes the name of a group.
func SetGroupName(jid string, name string) error {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return fmt.Errorf("not connected")
	}

	parsedJID, err := types.ParseJID(jid)
	if err != nil {
		return fmt.Errorf("invalid JID: %w", err)
	}

	return cli.SetGroupName(context.Background(), parsedJID, name)
}

// SetGroupTopic changes the topic/description of a group.
func SetGroupTopic(jid string, topic string) error {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return fmt.Errorf("not connected")
	}

	parsedJID, err := types.ParseJID(jid)
	if err != nil {
		return fmt.Errorf("invalid JID: %w", err)
	}

	return cli.SetGroupTopic(context.Background(), parsedJID, "", "", topic)
}

// LeaveGroup leaves a group chat.
func LeaveGroup(jid string) error {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return fmt.Errorf("not connected")
	}

	parsedJID, err := types.ParseJID(jid)
	if err != nil {
		return fmt.Errorf("invalid JID: %w", err)
	}

	return cli.LeaveGroup(context.Background(), parsedJID)
}

// UpdateGroupParticipants adds, removes, promotes, or demotes participants.
// action can be "add", "remove", "promote", or "demote".
func UpdateGroupParticipants(jid string, participantJIDsJSON string, action string) error {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return fmt.Errorf("not connected")
	}

	parsedJID, err := types.ParseJID(jid)
	if err != nil {
		return fmt.Errorf("invalid JID: %w", err)
	}

	var jidStrings []string
	if err := json.Unmarshal([]byte(participantJIDsJSON), &jidStrings); err != nil {
		return fmt.Errorf("invalid participant JIDs: %w", err)
	}

	var participants []types.JID
	for _, s := range jidStrings {
		j, err := types.ParseJID(s)
		if err == nil {
			participants = append(participants, j)
		}
	}

	var change whatsmeow.ParticipantChange
	switch action {
	case "add":
		change = whatsmeow.ParticipantChangeAdd
	case "remove":
		change = whatsmeow.ParticipantChangeRemove
	case "promote":
		change = whatsmeow.ParticipantChangePromote
	case "demote":
		change = whatsmeow.ParticipantChangeDemote
	default:
		return fmt.Errorf("invalid action: %s", action)
	}

	_, err = cli.UpdateGroupParticipants(context.Background(), parsedJID, participants, change)
	return err
}

// GetGroupInviteLink returns the invite link for a group. Set reset to true to revoke and get a new link.
func GetGroupInviteLink(jid string, reset bool) string {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return ""
	}

	parsedJID, err := types.ParseJID(jid)
	if err != nil {
		return ""
	}

	link, err := cli.GetGroupInviteLink(context.Background(), parsedJID, reset)
	if err != nil {
		return ""
	}

	return link
}

// JoinGroupWithLink joins a group using an invite link. Returns the group JID.
func JoinGroupWithLink(link string) string {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return ""
	}

	groupJID, err := cli.JoinGroupWithLink(context.Background(), link)
	if err != nil {
		return ""
	}

	return groupJID.String()
}

// --- Phase 4: Presence & Status ---

// SubscribePresence subscribes to presence updates for a JID.
func SubscribePresence(jid string) error {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return fmt.Errorf("not connected")
	}

	parsedJID, err := types.ParseJID(jid)
	if err != nil {
		return fmt.Errorf("invalid JID: %w", err)
	}

	return cli.SubscribePresence(context.Background(), parsedJID)
}

// SendPresence sends the user's presence state ("available" or "unavailable").
func SendPresence(presenceType string) error {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return fmt.Errorf("not connected")
	}

	var presence types.Presence
	switch presenceType {
	case "available":
		presence = types.PresenceAvailable
	case "unavailable":
		presence = types.PresenceUnavailable
	default:
		return fmt.Errorf("invalid presence type: %s", presenceType)
	}

	return cli.SendPresence(context.Background(), presence)
}

// SendChatPresence sends typing indicators for a chat.
func SendChatPresence(chatJID string, composing bool) error {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return fmt.Errorf("not connected")
	}

	parsedJID, err := types.ParseJID(chatJID)
	if err != nil {
		return fmt.Errorf("invalid JID: %w", err)
	}

	var state types.ChatPresence
	if composing {
		state = types.ChatPresenceComposing
	} else {
		state = types.ChatPresencePaused
	}

	return cli.SendChatPresence(context.Background(), parsedJID, state, types.ChatPresenceMediaText)
}

// GetProfilePicture returns the URL of the profile picture for a JID.
func GetProfilePicture(jid string) string {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return ""
	}

	parsedJID, err := types.ParseJID(jid)
	if err != nil {
		return ""
	}

	info, err := cli.GetProfilePictureInfo(context.Background(), parsedJID, &whatsmeow.GetProfilePictureParams{})
	if err != nil || info == nil {
		return ""
	}

	return info.URL
}

// IsOnWhatsApp checks if a phone number is registered on WhatsApp.
// Returns JSON with "jid" and "isRegistered" fields.
func IsOnWhatsApp(phone string) string {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return "{}"
	}

	results, err := cli.IsOnWhatsApp(context.Background(), []string{phone})
	if err != nil || len(results) == 0 {
		return "{}"
	}

	result := struct {
		JID          string `json:"jid"`
		IsRegistered bool   `json:"isRegistered"`
	}{
		JID:          results[0].JID.String(),
		IsRegistered: results[0].IsIn,
	}

	data, _ := json.Marshal(result)
	return string(data)
}

// --- Phase 6: Rich Content ---

// SendLocation sends a location message.
func SendLocation(chatJID string, lat float64, lng float64, name string, address string) error {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return fmt.Errorf("not connected")
	}

	parsedJID, err := types.ParseJID(chatJID)
	if err != nil {
		return fmt.Errorf("invalid JID: %w", err)
	}

	locMsg := &waE2E.LocationMessage{
		DegreesLatitude:  proto.Float64(lat),
		DegreesLongitude: proto.Float64(lng),
	}
	if name != "" {
		locMsg.Name = proto.String(name)
	}
	if address != "" {
		locMsg.Address = proto.String(address)
	}

	msg := &waE2E.Message{LocationMessage: locMsg}
	_, err = cli.SendMessage(context.Background(), parsedJID, msg)
	return err
}

// SendContact sends a contact card.
func SendContact(chatJID string, displayName string, vcard string) error {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return fmt.Errorf("not connected")
	}

	parsedJID, err := types.ParseJID(chatJID)
	if err != nil {
		return fmt.Errorf("invalid JID: %w", err)
	}

	contactMsg := &waE2E.ContactMessage{
		DisplayName: proto.String(displayName),
		Vcard:       proto.String(vcard),
	}

	msg := &waE2E.Message{ContactMessage: contactMsg}
	_, err = cli.SendMessage(context.Background(), parsedJID, msg)
	return err
}

// --- Phase 7: Privacy & Blocking ---

// GetBlocklist returns a JSON array of blocked JIDs.
func GetBlocklist() string {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return "[]"
	}

	blocklist, err := cli.GetBlocklist(context.Background())
	if err != nil {
		return "[]"
	}

	var jids []string
	for _, j := range blocklist.JIDs {
		jids = append(jids, j.String())
	}

	data, _ := json.Marshal(jids)
	return string(data)
}

// UpdateBlocklist blocks or unblocks a JID. action is "block" or "unblock".
func UpdateBlocklist(jid string, action string) error {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return fmt.Errorf("not connected")
	}

	parsedJID, err := types.ParseJID(jid)
	if err != nil {
		return fmt.Errorf("invalid JID: %w", err)
	}

	var blAction events.BlocklistChangeAction
	switch action {
	case "block":
		blAction = events.BlocklistChangeActionBlock
	case "unblock":
		blAction = events.BlocklistChangeActionUnblock
	default:
		return fmt.Errorf("invalid action: %s", action)
	}

	_, err = cli.UpdateBlocklist(context.Background(), parsedJID, blAction)
	return err
}

// GetPrivacySettings returns JSON with current privacy settings.
func GetPrivacySettings() string {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return "{}"
	}

	settings, err := cli.TryFetchPrivacySettings(context.Background(), false)
	if err != nil {
		return "{}"
	}

	data, _ := json.Marshal(settings)
	return string(data)
}

// SetStatusMessage sets the user's about text.
func SetStatusMessage(text string) error {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return fmt.Errorf("not connected")
	}

	return cli.SetStatusMessage(context.Background(), text)
}

// RejectCall notifies about an incoming call rejection.
// Note: whatsmeow does not expose a clean public call rejection API,
// so this logs the rejection. The OnCallOffer event is used to show
// a notification with caller info.
func RejectCall(callID string, callerJID string) error {
	mu.Lock()
	handler := eventHandler
	mu.Unlock()

	if handler != nil {
		handler.OnDebug(fmt.Sprintf("Call rejected: %s from %s", callID, callerJID))
	}
	return nil
}

// --- Phase 7: Newsletter/Channels ---

// GetSubscribedNewsletters returns JSON array of subscribed newsletter channels.
func GetSubscribedNewsletters() string {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return "[]"
	}

	newsletters, err := cli.GetSubscribedNewsletters(context.Background())
	if err != nil {
		return "[]"
	}

	type nlInfo struct {
		ID   string `json:"id"`
		Name string `json:"name"`
	}

	var result []nlInfo
	for _, nl := range newsletters {
		name := nl.ThreadMeta.Name.Text
		result = append(result, nlInfo{
			ID:   nl.ID.String(),
			Name: name,
		})
	}

	data, _ := json.Marshal(result)
	return string(data)
}

// GetContacts returns a JSON array of contacts
func GetContacts() string {
	mu.Lock()
	cli := client
	mu.Unlock()

	if cli == nil {
		return "[]"
	}

	contacts, err := cli.Store.Contacts.GetAllContacts(context.Background())
	if err != nil {
		return "[]"
	}

	type contactInfo struct {
		JID  string `json:"jid"`
		Name string `json:"name"`
	}

	var result []contactInfo
	for jid, info := range contacts {
		name := info.PushName
		if name == "" {
			name = info.FullName
		}
		if name == "" {
			name = info.BusinessName
		}
		result = append(result, contactInfo{
			JID:  jid.String(),
			Name: name,
		})
	}

	data, _ := json.Marshal(result)
	return string(data)
}

// GetChatList returns a JSON array of recent chats
func GetChatList() string {
	// This will be populated from history sync events
	// For now return empty array - chats come via OnHistorySync callback
	return "[]"
}

// GetChatMessages returns a JSON array of messages for a given JID
func GetChatMessages(jid string, count int) string {
	// Messages are stored on the Kotlin side via OnMessage/OnHistorySync callbacks
	return "[]"
}

// Logout clears the session and disconnects
func Logout() error {
	mu.Lock()
	defer mu.Unlock()

	if client != nil {
		err := client.Logout(context.Background())
		client = nil
		container = nil
		return err
	}
	return nil
}

// --- Event Handling ---

func handleEvent(rawEvt interface{}) {
	mu.Lock()
	handler := eventHandler
	mu.Unlock()

	if handler == nil {
		return
	}

	handler.OnDebug(fmt.Sprintf("event: %T", rawEvt))

	switch evt := rawEvt.(type) {
	case *events.Message:
		handleMessage(evt, handler)
	case *events.Connected:
		handler.OnDebug("Connected event fired")
		handler.OnConnected()
	case *events.Disconnected:
		handler.OnDebug("Disconnected event fired")
		mu.Lock()
		wasPairing := phonePairingActive
		phonePairingActive = false
		mu.Unlock()
		if wasPairing {
			handler.OnDebug("Was pairing — checking stored credentials...")
			go func() {
				time.Sleep(2 * time.Second)
				mu.Lock()
				// Tear down old client
				if client != nil {
					client.Disconnect()
					client = nil
				}
				mu.Unlock()

				handler.OnDebug("Creating fresh client...")
				mu.Lock()
				cli, err := getOrCreateClient()
				mu.Unlock()
				if err != nil {
					handler.OnDebug("Error creating client: " + err.Error())
					handler.OnDisconnected()
					return
				}

				// Check if we have stored credentials
				if cli.Store.ID == nil {
					handler.OnDebug("No stored credentials — pairing failed")
					handler.OnDisconnected()
					return
				}

				handler.OnDebug("Credentials found! Reconnecting...")
				err = cli.Connect()
				if err != nil {
					handler.OnDebug("Reconnect error: " + err.Error())
					handler.OnDisconnected()
				} else {
					handler.OnDebug("Reconnect succeeded!")
					// Connected event will fire naturally from the new client
				}
			}()
		} else {
			handler.OnDisconnected()
		}
	case *events.HistorySync:
		handleHistorySync(evt, handler)
	case *events.PairSuccess:
		handler.OnDebug("PairSuccess event fired! Reconnecting in 3s...")
		go func() {
			time.Sleep(3 * time.Second)
			handler.OnDebug("Tearing down old client...")
			mu.Lock()
			if client != nil {
				client.Disconnect()
				client = nil
			}
			mu.Unlock()

			handler.OnDebug("Creating fresh client from stored credentials...")
			mu.Lock()
			cli, err := getOrCreateClient()
			mu.Unlock()
			if err != nil {
				handler.OnDebug("getOrCreateClient error: " + err.Error())
				return
			}
			handler.OnDebug("Connecting fresh client...")
			err = cli.Connect()
			if err != nil {
				handler.OnDebug("Connect error: " + err.Error())
			} else {
				handler.OnDebug("Connect succeeded, calling OnConnected")
				handler.OnConnected()
			}
		}()

	// Phase 2: Receipt events
	case *events.Receipt:
		handleReceipt(evt, handler)

	// Phase 3: Group info update events
	case *events.GroupInfo:
		handleGroupInfoUpdate(evt, handler)

	// Phase 4: Presence events
	case *events.Presence:
		handlePresenceEvent(evt, handler)
	case *events.ChatPresence:
		handleChatPresenceEvent(evt, handler)

	// Phase 7: Call events
	case *events.CallOffer:
		handleCallOffer(evt, handler)
	}
}

// Expanded message payload struct with media fields
type msgPayload struct {
	ID            string `json:"id"`
	ChatJID       string `json:"chatJid"`
	SenderJID     string `json:"senderJid"`
	SenderName    string `json:"senderName"`
	Body          string `json:"body"`
	Timestamp     int64  `json:"timestamp"`
	IsFromMe      bool   `json:"isFromMe"`
	MessageType   string `json:"messageType"`
	MediaLocalPath string `json:"mediaLocalPath,omitempty"`
	MediaMimeType string `json:"mediaMimeType,omitempty"`
	MediaFileName string `json:"mediaFileName,omitempty"`
	MediaSize     uint64 `json:"mediaSize,omitempty"`
	MediaDuration uint32 `json:"mediaDuration,omitempty"`
	MediaWidth    uint32 `json:"mediaWidth,omitempty"`
	MediaHeight   uint32 `json:"mediaHeight,omitempty"`
	ThumbnailPath string `json:"thumbnailPath,omitempty"`
	Caption       string `json:"caption,omitempty"`
	// Quote context
	QuotedMessageId  string `json:"quotedMessageId,omitempty"`
	QuotedBody       string `json:"quotedBody,omitempty"`
	QuotedSenderName string `json:"quotedSenderName,omitempty"`
	// Reaction (for reaction messages)
	ReactionEmoji string `json:"reactionEmoji,omitempty"`
	ReactionTargetID string `json:"reactionTargetId,omitempty"`
	// Edit
	IsEdited bool `json:"isEdited,omitempty"`
	EditTargetID string `json:"editTargetId,omitempty"`
	// Revoke
	IsRevoked bool `json:"isRevoked,omitempty"`
	RevokeTargetID string `json:"revokeTargetId,omitempty"`
}

// saveThumbnail saves JPEG thumbnail data to disk and returns the path
func saveThumbnail(thumb []byte) string {
	mu.Lock()
	mp := mediaPath
	mu.Unlock()

	if mp == "" || len(thumb) == 0 {
		return ""
	}

	thumbDir := filepath.Join(mp, "thumbnails")
	os.MkdirAll(thumbDir, 0755)

	path := filepath.Join(thumbDir, randomFileName(".jpg"))
	if err := os.WriteFile(path, thumb, 0644); err != nil {
		return ""
	}
	return path
}

// downloadAndSave downloads media from a whatsmeow downloadable and saves to disk
func downloadAndSave(cli *whatsmeow.Client, dl whatsmeow.DownloadableMessage, ext string) string {
	mu.Lock()
	mp := mediaPath
	mu.Unlock()

	if mp == "" {
		return ""
	}

	data, err := cli.Download(context.Background(), dl)
	if err != nil {
		return ""
	}

	path := filepath.Join(mp, randomFileName(ext))
	if err := os.WriteFile(path, data, 0644); err != nil {
		return ""
	}
	return path
}

func extFromMime(mime string) string {
	switch {
	case strings.HasPrefix(mime, "image/jpeg"):
		return ".jpg"
	case strings.HasPrefix(mime, "image/png"):
		return ".png"
	case strings.HasPrefix(mime, "image/webp"):
		return ".webp"
	case strings.HasPrefix(mime, "video/mp4"):
		return ".mp4"
	case strings.HasPrefix(mime, "audio/ogg"):
		return ".ogg"
	case strings.HasPrefix(mime, "audio/mpeg"):
		return ".mp3"
	case strings.HasPrefix(mime, "application/pdf"):
		return ".pdf"
	default:
		return ".bin"
	}
}

func handleMessage(evt *events.Message, handler EventHandler) {
	mu.Lock()
	cli := client
	mu.Unlock()

	waMsg := evt.Message

	// Check for reaction messages
	if reaction := waMsg.GetReactionMessage(); reaction != nil {
		payload := msgPayload{
			ID:               evt.Info.ID,
			ChatJID:          evt.Info.Chat.String(),
			SenderJID:        evt.Info.Sender.String(),
			SenderName:       evt.Info.PushName,
			Timestamp:        evt.Info.Timestamp.Unix(),
			IsFromMe:         evt.Info.IsFromMe,
			MessageType:      "reaction",
			ReactionEmoji:    reaction.GetText(),
			ReactionTargetID: reaction.GetKey().GetID(),
		}
		data, _ := json.Marshal(payload)
		handler.OnReaction(string(data))
		return
	}

	// Check for protocol messages (edits, revokes)
	if protoMsg := waMsg.GetProtocolMessage(); protoMsg != nil {
		// Edit
		if protoMsg.GetType() == waE2E.ProtocolMessage_MESSAGE_EDIT {
			editedMsg := protoMsg.GetEditedMessage()
			newBody := ""
			if editedMsg != nil {
				if editedMsg.GetConversation() != "" {
					newBody = editedMsg.GetConversation()
				} else if editedMsg.GetExtendedTextMessage() != nil {
					newBody = editedMsg.GetExtendedTextMessage().GetText()
				}
			}
			payload := struct {
				ChatJID       string `json:"chatJid"`
				TargetID      string `json:"targetId"`
				SenderJID     string `json:"senderJid"`
				NewBody       string `json:"newBody"`
				Timestamp     int64  `json:"timestamp"`
			}{
				ChatJID:   evt.Info.Chat.String(),
				TargetID:  protoMsg.GetKey().GetID(),
				SenderJID: evt.Info.Sender.String(),
				NewBody:   newBody,
				Timestamp: evt.Info.Timestamp.Unix(),
			}
			data, _ := json.Marshal(payload)
			handler.OnMessageEdit(string(data))
			return
		}

		// Revoke
		if protoMsg.GetType() == waE2E.ProtocolMessage_REVOKE {
			payload := struct {
				ChatJID  string `json:"chatJid"`
				TargetID string `json:"targetId"`
				SenderJID string `json:"senderJid"`
				Timestamp int64  `json:"timestamp"`
			}{
				ChatJID:   evt.Info.Chat.String(),
				TargetID:  protoMsg.GetKey().GetID(),
				SenderJID: evt.Info.Sender.String(),
				Timestamp: evt.Info.Timestamp.Unix(),
			}
			data, _ := json.Marshal(payload)
			handler.OnMessageRevoke(string(data))
			return
		}
	}

	payload := msgPayload{
		ID:          evt.Info.ID,
		ChatJID:     evt.Info.Chat.String(),
		SenderJID:   evt.Info.Sender.String(),
		SenderName:  evt.Info.PushName,
		Timestamp:   evt.Info.Timestamp.Unix(),
		IsFromMe:    evt.Info.IsFromMe,
		MessageType: "text",
	}

	// Extract context info (quotes) from any message type
	extractContextInfo := func(ctx *waE2E.ContextInfo) {
		if ctx == nil {
			return
		}
		if ctx.GetStanzaID() != "" {
			payload.QuotedMessageId = ctx.GetStanzaID()
			payload.QuotedSenderName = ctx.GetParticipant()
			if quoted := ctx.GetQuotedMessage(); quoted != nil {
				if quoted.GetConversation() != "" {
					payload.QuotedBody = quoted.GetConversation()
				} else if quoted.GetExtendedTextMessage() != nil {
					payload.QuotedBody = quoted.GetExtendedTextMessage().GetText()
				}
			}
		}
	}

	// Detect message type and extract fields
	if img := waMsg.GetImageMessage(); img != nil {
		payload.MessageType = "image"
		payload.MediaMimeType = img.GetMimetype()
		payload.MediaSize = img.GetFileLength()
		payload.MediaWidth = img.GetWidth()
		payload.MediaHeight = img.GetHeight()
		payload.Caption = img.GetCaption()
		payload.Body = img.GetCaption()
		payload.ThumbnailPath = saveThumbnail(img.GetJPEGThumbnail())
		extractContextInfo(img.GetContextInfo())
		// Auto-download
		if cli != nil {
			payload.MediaLocalPath = downloadAndSave(cli, img, extFromMime(img.GetMimetype()))
		}
	} else if vid := waMsg.GetVideoMessage(); vid != nil {
		payload.MessageType = "video"
		payload.MediaMimeType = vid.GetMimetype()
		payload.MediaSize = vid.GetFileLength()
		payload.MediaWidth = vid.GetWidth()
		payload.MediaHeight = vid.GetHeight()
		payload.MediaDuration = vid.GetSeconds()
		payload.Caption = vid.GetCaption()
		payload.Body = vid.GetCaption()
		payload.ThumbnailPath = saveThumbnail(vid.GetJPEGThumbnail())
		extractContextInfo(vid.GetContextInfo())
		if cli != nil {
			payload.MediaLocalPath = downloadAndSave(cli, vid, extFromMime(vid.GetMimetype()))
		}
	} else if audio := waMsg.GetAudioMessage(); audio != nil {
		if audio.GetPTT() {
			payload.MessageType = "ptt"
		} else {
			payload.MessageType = "audio"
		}
		payload.MediaMimeType = audio.GetMimetype()
		payload.MediaSize = audio.GetFileLength()
		payload.MediaDuration = audio.GetSeconds()
		extractContextInfo(audio.GetContextInfo())
		if cli != nil {
			payload.MediaLocalPath = downloadAndSave(cli, audio, extFromMime(audio.GetMimetype()))
		}
	} else if doc := waMsg.GetDocumentMessage(); doc != nil {
		payload.MessageType = "document"
		payload.MediaMimeType = doc.GetMimetype()
		payload.MediaSize = doc.GetFileLength()
		payload.MediaFileName = doc.GetFileName()
		payload.Caption = doc.GetCaption()
		payload.Body = doc.GetFileName()
		payload.ThumbnailPath = saveThumbnail(doc.GetJPEGThumbnail())
		extractContextInfo(doc.GetContextInfo())
		if cli != nil {
			payload.MediaLocalPath = downloadAndSave(cli, doc, extFromMime(doc.GetMimetype()))
		}
	} else if sticker := waMsg.GetStickerMessage(); sticker != nil {
		payload.MessageType = "sticker"
		payload.MediaMimeType = sticker.GetMimetype()
		payload.MediaSize = sticker.GetFileLength()
		payload.MediaWidth = sticker.GetWidth()
		payload.MediaHeight = sticker.GetHeight()
		extractContextInfo(sticker.GetContextInfo())
		if cli != nil {
			payload.MediaLocalPath = downloadAndSave(cli, sticker, extFromMime(sticker.GetMimetype()))
		}
	} else if loc := waMsg.GetLocationMessage(); loc != nil {
		payload.MessageType = "location"
		payload.Body = fmt.Sprintf("%f,%f", loc.GetDegreesLatitude(), loc.GetDegreesLongitude())
		if loc.GetName() != "" {
			payload.Caption = loc.GetName()
		}
		if loc.GetAddress() != "" {
			if payload.Caption != "" {
				payload.Caption += "\n"
			}
			payload.Caption += loc.GetAddress()
		}
		payload.ThumbnailPath = saveThumbnail(loc.GetJPEGThumbnail())
	} else if contact := waMsg.GetContactMessage(); contact != nil {
		payload.MessageType = "contact"
		payload.Body = contact.GetDisplayName()
		payload.Caption = contact.GetVcard()
	} else if waMsg.GetConversation() != "" {
		payload.Body = waMsg.GetConversation()
	} else if ext := waMsg.GetExtendedTextMessage(); ext != nil {
		payload.Body = ext.GetText()
		extractContextInfo(ext.GetContextInfo())
	}

	// Handle ephemeral wrapper
	if ephemeral := waMsg.GetEphemeralMessage(); ephemeral != nil {
		// Recursively handle the inner message
		innerEvt := *evt
		innerEvt.Message = ephemeral.GetMessage()
		handleMessage(&innerEvt, handler)
		return
	}

	// Handle view-once wrapper
	if viewOnce := waMsg.GetViewOnceMessage(); viewOnce != nil {
		innerEvt := *evt
		innerEvt.Message = viewOnce.GetMessage()
		handleMessage(&innerEvt, handler)
		return
	}

	data, _ := json.Marshal(payload)
	handler.OnMessage(string(data))
}

func handleHistorySync(evt *events.HistorySync, handler EventHandler) {
	type historyMessage struct {
		ID            string `json:"id"`
		ChatJID       string `json:"chatJid"`
		SenderJID     string `json:"senderJid"`
		SenderName    string `json:"senderName"`
		Body          string `json:"body"`
		Timestamp     int64  `json:"timestamp"`
		IsFromMe      bool   `json:"isFromMe"`
		MessageType   string `json:"messageType"`
		MediaMimeType string `json:"mediaMimeType,omitempty"`
		MediaFileName string `json:"mediaFileName,omitempty"`
		MediaSize     uint64 `json:"mediaSize,omitempty"`
		MediaDuration uint32 `json:"mediaDuration,omitempty"`
		Caption       string `json:"caption,omitempty"`
		ThumbnailPath string `json:"thumbnailPath,omitempty"`
	}

	type historyChat struct {
		JID             string           `json:"jid"`
		Name            string           `json:"name"`
		LastMessage     string           `json:"lastMessage"`
		LastMsgTime     int64            `json:"lastMsgTime"`
		UnreadCount     int              `json:"unreadCount"`
		IsGroup         bool             `json:"isGroup"`
		LastMessageType string           `json:"lastMessageType"`
		Messages        []historyMessage `json:"messages"`
	}

	var chats []historyChat

	for _, conv := range evt.Data.GetConversations() {
		jid := conv.GetID()
		parsedJID, _ := types.ParseJID(jid)
		isGroup := parsedJID.Server == types.GroupServer

		chatName := conv.GetDisplayName()
		if chatName == "" {
			chatName = conv.GetName()
		}

		// Try contact store for name
		if chatName == "" {
			mu.Lock()
			cli := client
			mu.Unlock()
			if cli != nil {
				contactInfo, err := cli.Store.Contacts.GetContact(context.Background(), parsedJID)
				if err == nil {
					if contactInfo.PushName != "" {
						chatName = contactInfo.PushName
					} else if contactInfo.FullName != "" {
						chatName = contactInfo.FullName
					} else if contactInfo.BusinessName != "" {
						chatName = contactInfo.BusinessName
					}
				}
			}
		}

		var messages []historyMessage
		var lastMsg string
		var lastMsgType string
		var lastTime int64
		var pushName string

		for _, histMsg := range conv.GetMessages() {
			msg := histMsg.GetMessage()
			if msg == nil || msg.GetMessage() == nil {
				continue
			}

			waMsg := msg.GetMessage()
			hm := historyMessage{
				MessageType: "text",
			}

			// Detect message types in history
			if img := waMsg.GetImageMessage(); img != nil {
				hm.MessageType = "image"
				hm.Body = img.GetCaption()
				hm.MediaMimeType = img.GetMimetype()
				hm.MediaSize = img.GetFileLength()
				hm.Caption = img.GetCaption()
				hm.ThumbnailPath = saveThumbnail(img.GetJPEGThumbnail())
			} else if vid := waMsg.GetVideoMessage(); vid != nil {
				hm.MessageType = "video"
				hm.Body = vid.GetCaption()
				hm.MediaMimeType = vid.GetMimetype()
				hm.MediaSize = vid.GetFileLength()
				hm.MediaDuration = vid.GetSeconds()
				hm.Caption = vid.GetCaption()
				hm.ThumbnailPath = saveThumbnail(vid.GetJPEGThumbnail())
			} else if audio := waMsg.GetAudioMessage(); audio != nil {
				if audio.GetPTT() {
					hm.MessageType = "ptt"
				} else {
					hm.MessageType = "audio"
				}
				hm.MediaMimeType = audio.GetMimetype()
				hm.MediaSize = audio.GetFileLength()
				hm.MediaDuration = audio.GetSeconds()
			} else if doc := waMsg.GetDocumentMessage(); doc != nil {
				hm.MessageType = "document"
				hm.Body = doc.GetFileName()
				hm.MediaMimeType = doc.GetMimetype()
				hm.MediaSize = doc.GetFileLength()
				hm.MediaFileName = doc.GetFileName()
				hm.ThumbnailPath = saveThumbnail(doc.GetJPEGThumbnail())
			} else if sticker := waMsg.GetStickerMessage(); sticker != nil {
				hm.MessageType = "sticker"
				hm.MediaMimeType = sticker.GetMimetype()
			} else if waMsg.GetLocationMessage() != nil {
				hm.MessageType = "location"
				loc := waMsg.GetLocationMessage()
				hm.Body = fmt.Sprintf("%f,%f", loc.GetDegreesLatitude(), loc.GetDegreesLongitude())
			} else if waMsg.GetContactMessage() != nil {
				hm.MessageType = "contact"
				hm.Body = waMsg.GetContactMessage().GetDisplayName()
			} else if waMsg.GetConversation() != "" {
				hm.Body = waMsg.GetConversation()
			} else if waMsg.GetExtendedTextMessage() != nil {
				hm.Body = waMsg.GetExtendedTextMessage().GetText()
			}

			info := msg.GetKey()
			senderJID := jid
			if info.GetParticipant() != "" {
				senderJID = info.GetParticipant()
			}

			// Extract push name from message
			if msg.GetPushName() != "" && !info.GetFromMe() {
				pushName = msg.GetPushName()
			}

			ts := int64(msg.GetMessageTimestamp())
			if ts == 0 {
				ts = time.Now().Unix()
			}

			hm.ID = info.GetID()
			hm.ChatJID = jid
			hm.SenderJID = senderJID
			hm.SenderName = msg.GetPushName()
			hm.Timestamp = ts
			hm.IsFromMe = info.GetFromMe()

			messages = append(messages, hm)

			if ts > lastTime {
				lastTime = ts
				lastMsg = hm.Body
				lastMsgType = hm.MessageType
			}
		}

		// Fall back to push name from messages
		if chatName == "" && pushName != "" {
			chatName = pushName
		}
		if chatName == "" {
			chatName = jid
		}

		if lastMsgType == "" {
			lastMsgType = "text"
		}

		chat := historyChat{
			JID:             jid,
			Name:            chatName,
			LastMessage:     lastMsg,
			LastMsgTime:     lastTime,
			UnreadCount:     int(conv.GetUnreadCount()),
			IsGroup:         isGroup,
			LastMessageType: lastMsgType,
			Messages:        messages,
		}
		chats = append(chats, chat)
	}

	data, _ := json.Marshal(chats)
	handler.OnHistorySync(string(data))
}

func handleReceipt(evt *events.Receipt, handler EventHandler) {
	status := "delivered"
	switch evt.Type {
	case events.ReceiptTypeRead:
		status = "read"
	case events.ReceiptTypeDelivered:
		status = "delivered"
	case events.ReceiptTypePlayed:
		status = "played"
	}

	payload := struct {
		ChatJID    string   `json:"chatJid"`
		SenderJID  string   `json:"senderJid"`
		MessageIDs []string `json:"messageIds"`
		Status     string   `json:"status"`
		Timestamp  int64    `json:"timestamp"`
	}{
		ChatJID:    evt.Chat.String(),
		SenderJID:  evt.Sender.String(),
		MessageIDs: evt.MessageIDs,
		Status:     status,
		Timestamp:  evt.Timestamp.Unix(),
	}

	data, _ := json.Marshal(payload)
	handler.OnReceipt(string(data))
}

func handleGroupInfoUpdate(evt *events.GroupInfo, handler EventHandler) {
	payload := struct {
		JID       string `json:"jid"`
		Name      string `json:"name,omitempty"`
		Topic     string `json:"topic,omitempty"`
		SenderJID string `json:"senderJid"`
		Timestamp int64  `json:"timestamp"`
	}{
		JID:       evt.JID.String(),
		SenderJID: evt.Sender.String(),
		Timestamp: evt.Timestamp.Unix(),
	}

	if evt.Name != nil {
		payload.Name = evt.Name.Name
	}
	if evt.Topic != nil {
		payload.Topic = evt.Topic.Topic
	}

	data, _ := json.Marshal(payload)
	handler.OnGroupInfoUpdate(string(data))
}

func handlePresenceEvent(evt *events.Presence, handler EventHandler) {
	payload := struct {
		JID        string `json:"jid"`
		Available  bool   `json:"available"`
		LastSeen   int64  `json:"lastSeen"`
	}{
		JID:       evt.From.String(),
		Available: !evt.Unavailable,
		LastSeen:  evt.LastSeen.Unix(),
	}

	data, _ := json.Marshal(payload)
	handler.OnPresence(string(data))
}

func handleChatPresenceEvent(evt *events.ChatPresence, handler EventHandler) {
	payload := struct {
		ChatJID   string `json:"chatJid"`
		SenderJID string `json:"senderJid"`
		State     string `json:"state"`
		Media     string `json:"media"`
	}{
		ChatJID:   evt.MessageSource.Chat.String(),
		SenderJID: evt.MessageSource.Sender.String(),
		State:     string(evt.State),
		Media:     string(evt.Media),
	}

	data, _ := json.Marshal(payload)
	handler.OnChatPresence(string(data))
}

func handleCallOffer(evt *events.CallOffer, handler EventHandler) {
	payload := struct {
		CallID    string `json:"callId"`
		CallerJID string `json:"callerJid"`
		Timestamp int64  `json:"timestamp"`
	}{
		CallID:    evt.CallID,
		CallerJID: evt.CallCreator.String(),
		Timestamp: evt.Timestamp.Unix(),
	}

	data, _ := json.Marshal(payload)
	handler.OnCallOffer(string(data))
}
