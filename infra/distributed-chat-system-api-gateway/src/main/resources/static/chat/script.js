document.addEventListener('DOMContentLoaded', () => {
  checkMyInfoAndRedirect();
  initializeLogoutButton();
  initializeWebSocketButtons();
  initializeTeamSection();
});

async function checkMyInfoAndRedirect() {
  try {
    const response = await fetch('/v1/private/api/user/my-info', {
      method: 'GET',
      headers: {'Content-Type': 'application/json'},
    });
    const data = await response.json();
    if (!response.ok || data.code !== 0) {
      window.location.href = '/';
    }
  } catch (error) {
    console.error('Error verifying login:', error);
    window.location.href = '/';
  }
}

function initializeLogoutButton() {
  document.getElementById('logout').addEventListener('click', async () => {
    try {
      const response = await fetch('/v1/private/api/user/logout', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
      });
      if (response.ok) {
        window.location.href = '/';
      }
    } catch (error) {
      console.error('Logout error:', error);
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
  webSocket = new WebSocket('ws://localhost:8080/websocket');
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
  const input = document.getElementById('message');
  const message = input.value;
  if (webSocket?.readyState === WebSocket.OPEN) {
    webSocket.send(message);
    showMessage(message, true);
    input.value = '';
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
  document.getElementById('connect').addEventListener('click',
      connectWebSocket);
  document.getElementById('disconnect').addEventListener('click',
      disconnectWebSocket);
  document.getElementById('send').addEventListener('click', sendMessage);
}

function initializeTeamSection() {
  fetchTeams();

  const form = document.getElementById('create-team-form');
  form?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const teamName = document.getElementById('team-name').value;
    try {
      const response = await fetch('/v1/private/api/team/team', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({teamName}),
      });
      const data = await response.json();
      if (data.code === 0) {
        document.getElementById('team-name').value = '';
        fetchTeams();
        showTeamAlert(`팀 등록 완료: ${teamName}`);
      } else {
        showTeamAlert(`에러: ${data.msg}`, true);
      }
    } catch (error) {
      console.error('팀 등록 오류:', error);
    }
  });

  const searchInput = document.getElementById('team-name');
  searchInput?.addEventListener('input', () => {
    fetchTeams(searchInput.value);
  });

  const pagination = document.getElementById('team-pagination');
  pagination?.addEventListener('click', (e) => {
    const page = e.target.dataset.page;
    if (page) {
      fetchTeams(undefined, parseInt(page));
    }
  });
}

function showTeamAlert(message, isError = false) {
  const alertBox = document.getElementById('team-alert');
  alertBox.textContent = message;
  alertBox.style.color = isError ? 'red' : 'green';
}

async function fetchTeams(keyword = '', pageNumber = 1, pageSize = 10) {
  try {
    const response = await fetch(
        `/v1/private/api/team/teams?teamName=${encodeURIComponent(
            keyword)}&pageNumber=${pageNumber}&pageSize=${pageSize}`);
    const data = await response.json();
    const teamList = document.getElementById('team-list');
    teamList.innerHTML = '';
    if (data.code === 0 && data.data?.list) {
      data.data.list.forEach(team => {
        const li = document.createElement('li');
        li.className = 'list-group-item';
        li.textContent = `${team.teamName} (ID: ${team.teamId})`;
        teamList.appendChild(li);
      });
      renderPagination(data.data.paging);
    }
  } catch (error) {
    console.error('팀 목록 조회 오류:', error);
  }
}

function renderPagination(paging) {
  const pagination = document.getElementById('team-pagination');
  if (!pagination || !paging) {
    return;
  }

  let html = '';
  for (let i = 1; i <= paging.totalPageCount; i++) {
    html += `<button data-page="${i}" class="btn btn-sm ${paging.pageNumber
    === i ? 'btn-primary' : 'btn-default'}">${i}</button> `;
  }
  pagination.innerHTML = html;
}
