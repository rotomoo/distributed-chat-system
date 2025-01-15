let script;

function onOpen() {
  console.log("Connected: onOpen()");
}

function onMessage(receivedMessage) {
  showMessage(receivedMessage.data, false);
  console.log("Received: onMessage()");
}

function onClose() {
  console.log("Disconnected: onClose()");
}

function connect() {
  script = new WebSocket("ws://localhost:7777/ws/chats");
  script.onmessage = onMessage;
  script.onopen = onOpen;
  script.onclose = onClose;

  setConnected(true);
  console.log("Connected: connect()");
}

function disconnect() {
  script.close();
  setConnected(false);
  console.log("Disconnected: disconnect()");
}

function sendMessage() {
  let message = document.getElementById("message");
  script.send(message.value);
  showMessage(message.value, true);
  message.value = "";
  console.log("Sent: send()");
}

function showMessage(message, isSelf) {
  let messagesDiv = document.getElementById("messages");
  let messageRow = document.createElement("div");
  messageRow.className = `message-row ${isSelf ? 'self' : ''}`;

  let messageContent = document.createElement("div");
  messageContent.className = "message-content";
  messageContent.textContent = message;

  messageRow.appendChild(messageContent);
  messagesDiv.appendChild(messageRow);
  messagesDiv.scrollTop = messagesDiv.scrollHeight;
}

function setConnected(connected) {
  document.getElementById("connect").disabled = connected;
  document.getElementById("disconnect").disabled = !connected;
  document.getElementById("messages").innerHTML = "";
}

$(function () {
  $("#connect").click(() => connect());
  $("#disconnect").click(() => disconnect());
  $("#send").click(() => sendMessage());
});