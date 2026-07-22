const form = document.getElementById('shorten-form');
const urlInput = document.getElementById('url-input');
const resultBox = document.getElementById('result');
const linksBody = document.getElementById('links-body');

// Purely for this demo page's convenience (so the table survives a
// refresh) - the backend itself is the source of truth for all data.
const STORAGE_KEY = 'demo_short_links';

function loadLinks() {
    try {
        return JSON.parse(localStorage.getItem(STORAGE_KEY)) || [];
    } catch {
        return [];
    }
}

function saveLinks(links) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(links));
}

function renderLinks() {
    const links = loadLinks();
    linksBody.innerHTML = '';

    links.forEach(link => {
        const row = document.createElement('tr');
        const shortUrl = `${window.location.origin}/${link.shortCode}`;

        row.innerHTML = `
            <td><a href="${shortUrl}" target="_blank" rel="noopener">${shortUrl}</a></td>
            <td title="${link.url}">${truncate(link.url, 40)}</td>
            <td>${new Date(link.createdAt).toLocaleString()}</td>
            <td>
                <span class="action-link" data-action="stats" data-code="${link.shortCode}">stats</span>
                <span class="action-link" data-action="delete" data-code="${link.shortCode}">delete</span>
            </td>
        `;
        linksBody.appendChild(row);
    });
}

function truncate(str, max) {
    return str.length > max ? str.slice(0, max) + '…' : str;
}

function showResult(message, isError = false) {
    resultBox.textContent = message;
    resultBox.classList.remove('hidden');
    resultBox.classList.toggle('error', isError);
}

form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const url = urlInput.value.trim();

    try {
        const res = await fetch('/shorten', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url })
        });

        const data = await res.json();

        if (!res.ok) {
            const messages = (data.messages || ['Something went wrong']).join(', ');
            showResult(`Error: ${messages}`, true);
            return;
        }

        const links = loadLinks();
        links.unshift(data);
        saveLinks(links);
        renderLinks();

        const shortUrl = `${window.location.origin}/${data.shortCode}`;
        showResult(`Created: ${shortUrl}`);
        urlInput.value = '';
    } catch (err) {
        showResult('Network error: could not reach the API', true);
    }
});

linksBody.addEventListener('click', async (e) => {
    const target = e.target;
    if (!target.dataset.action) return;

    const code = target.dataset.code;

    if (target.dataset.action === 'delete') {
        const res = await fetch(`/shorten/${code}`, { method: 'DELETE' });
        if (res.status === 204) {
            const links = loadLinks().filter(l => l.shortCode !== code);
            saveLinks(links);
            renderLinks();
            showResult(`Deleted ${code}`);
        } else {
            showResult(`Could not delete ${code}`, true);
        }
    }

    if (target.dataset.action === 'stats') {
        const res = await fetch(`/shorten/${code}/stats`);
        if (res.ok) {
            const data = await res.json();
            showResult(`${code} has been accessed ${data.accessCount} time(s)`);
        } else {
            showResult(`Could not load stats for ${code}`, true);
        }
    }
});

renderLinks();
