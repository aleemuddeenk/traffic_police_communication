// ClearPath PWA Service Worker
const CACHE_NAME = 'clearpath-v1';
const ASSETS_TO_CACHE = [
  '/',
  '/index.html',
  '/css/clearpath.css',
  '/js/clearpath-app.js',
  '/js/audio-siren.js',
  '/manifest.json'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(ASSETS_TO_CACHE);
    }).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))
      );
    }).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  // Pass through API and WebSocket requests
  if (event.request.url.includes('/api/') || event.request.url.includes('/ws')) {
    return;
  }
  event.respondWith(
    caches.match(event.request).then((response) => {
      return response || fetch(event.request);
    })
  );
});

// Background push notification event listener (Firebase Cloud Messaging)
self.addEventListener('push', (event) => {
  let data = { title: 'EMERGENCY: Ambulance Approaching', body: 'Clear traffic route ahead!' };
  if (event.data) {
    try {
      data = event.data.json();
    } catch (e) {
      data = { title: 'EMERGENCY ALERT', body: event.data.text() };
    }
  }

  const options = {
    body: data.body,
    icon: '/manifest.json',
    badge: '/manifest.json',
    vibrate: [500, 200, 500, 200, 500],
    data: data.data || {},
    requireInteraction: true,
    actions: [
      { action: 'ack', title: "I'm Clearing Road" },
      { action: 'dismiss', title: 'Dismiss' }
    ]
  };

  event.waitUntil(
    self.registration.showNotification(data.title, options)
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if (client.url.includes('/') && 'focus' in client) {
          return client.focus();
        }
      }
      if (clients.openWindow) {
        return clients.openWindow('/');
      }
    })
  );
});
