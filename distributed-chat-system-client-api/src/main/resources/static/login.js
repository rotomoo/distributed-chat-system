// Handle Login Form Submission
document.getElementById('loginForm').addEventListener('submit', async function (e) {
    e.preventDefault();

    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;

    try {
        const response = await fetch('/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: `username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}`,
        });

        if (response.ok) {
            alert('Login Successful!');
            window.location.href = '/home'; // Redirect after successful login
        } else {
            alert('Login Failed. Please check your credentials.');
        }
    } catch (error) {
        console.error('Error during login:', error);
        alert('An error occurred. Please try again later.');
    }
});

// Handle Register Button Click
document.getElementById('registerBtn').addEventListener('click', function () {
    window.location.href = '/register';
});
