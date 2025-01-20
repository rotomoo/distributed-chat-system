// Tab switching
const signupTab = document.getElementById('signup-tab');
const loginTab = document.getElementById('login-tab');
const signupSection = document.getElementById('signup-section');
const loginSection = document.getElementById('login-section');

signupTab.addEventListener('click', () => {
  signupTab.classList.add('active');
  loginTab.classList.remove('active');
  signupSection.classList.add('active');
  loginSection.classList.remove('active');
});

loginTab.addEventListener('click', () => {
  loginTab.classList.add('active');
  signupTab.classList.remove('active');
  loginSection.classList.add('active');
  signupSection.classList.remove('active');
});

// 회원가입 요청
document.getElementById('signup-form').addEventListener('submit',
    async (event) => {
      event.preventDefault();
      const account = document.getElementById('signup-account').value;
      const password = document.getElementById('signup-password').value;
      const messageElement = document.getElementById('signup-message');

      try {
        const response = await fetch('/v1/public/api/auth/signup', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({account, password}),
        });

        if (response.ok) {
          const data = await response.json();
          messageElement.style.color = 'green';
          messageElement.textContent = `회원가입 성공! User ID: ${data.data.userId}`;
        } else {
          const error = await response.json();
          messageElement.style.color = 'red';
          messageElement.textContent = `에러: ${error.msg || '회원가입에 실패했습니다.'}`;
        }
      } catch (error) {
        console.error('Error:', error);
        messageElement.style.color = 'red';
        messageElement.textContent = '서버와 연결할 수 없습니다.';
      }
    });

// 로그인 요청
document.getElementById('login-form').addEventListener('submit',
    async (event) => {
      event.preventDefault();
      const account = document.getElementById('login-account').value;
      const password = document.getElementById('login-password').value;
      const messageElement = document.getElementById('login-message');

      try {
        const response = await fetch('/v1/public/api/auth/login', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
          },
          body: new URLSearchParams({account, password}),
        });

        if (response.ok) {
          messageElement.style.color = 'green';
          messageElement.textContent = `로그인 성공!`;
          window.location.reload();
        } else {
          const error = await response.json();
          messageElement.style.color = 'red';
          messageElement.textContent = `에러: ${error.msg
          || '로그인에 실패했습니다. 아이디 비밀번호를 확인해주세요.'}`;
        }
      } catch (error) {
        console.error('Error:', error);
        messageElement.style.color = 'red';
        messageElement.textContent = '서버와 연결할 수 없습니다.';
      }
    });
