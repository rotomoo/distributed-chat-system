// 내 정보 조회 후 로그인 페이지 이동
document.addEventListener('DOMContentLoaded', () => {
  checkMyInfoAndRedirect();
  initializeLogoutButton();
  initializeWebSocketButtons();
});

async function checkMyInfoAndRedirect() {
  try {
    const response = await fetch('/v1/private/api/user/my-info', {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });

    if (response.ok) {
      const data = await response.json();
      if (data.code !== 0) {
        window.location.href = '/';
      }
      console.log('User Info:', data);
    } else {
      console.error('Failed to verify login:', response.statusText);
      window.location.href = '/';
    }
  } catch (error) {
    console.error('Error verifying login:', error);
    window.location.href = '/';
  }
}

function initializeLogoutButton() {
  const logoutButton = document.getElementById('logout');
  logoutButton.addEventListener('click', async () => {
    try {
      const response = await fetch('/v1/private/api/user/logout', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
      });

      if (response.ok) {
        window.location.href = '/';
      } else {
        console.error('Failed to logout:', response.statusText);
      }
    } catch (error) {
      console.error('Error during logout:', error);
    }
  });
}

let webSocket;

function onOpen() {
  console.log('WebSocket connected');
}

function onMessage(event) {
  showMessage(event.data, false);
}

function onClose() {
  console.log('WebSocket disconnected');
}

function connectWebSocket() {
  webSocket = new WebSocket('ws://localhost:8080/ws/chats');
  webSocket.onopen = onOpen;
  webSocket.onmessage = onMessage;
  webSocket.onclose = onClose;

  setConnected(true);
}

function disconnectWebSocket() {
  if (webSocket) {
    webSocket.close();
    setConnected(false);
  }
}

function sendMessage() {
  const messageInput = document.getElementById('message');
  const message = messageInput.value;
  if (webSocket && webSocket.readyState === WebSocket.OPEN) {
    webSocket.send(message);
    showMessage(message, true);
    messageInput.value = '';
  } else {
    console.error('WebSocket is not connected.');
  }
}

function showMessage(message, isSelf) {
  const messagesDiv = document.getElementById('messages');
  const messageRow = document.createElement('div');
  messageRow.className = `message-row ${isSelf ? 'self' : ''}`;

  const messageContent = document.createElement('div');
  messageContent.className = 'message-content';
  messageContent.textContent = message;

  messageRow.appendChild(messageContent);
  messagesDiv.appendChild(messageRow);
  messagesDiv.scrollTop = messagesDiv.scrollHeight;
}

function setConnected(connected) {
  document.getElementById('connect').disabled = connected;
  document.getElementById('disconnect').disabled = !connected;
  document.getElementById('messages').innerHTML = '';
}

function initializeWebSocketButtons() {
  const connectButton = document.getElementById('connect');
  const disconnectButton = document.getElementById('disconnect');
  const sendButton = document.getElementById('send');

  connectButton.addEventListener('click', connectWebSocket);
  disconnectButton.addEventListener('click', disconnectWebSocket);
  sendButton.addEventListener('click', sendMessage);
}