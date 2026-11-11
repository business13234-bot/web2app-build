// Extra app features. Every feature has its own options.
// The website draws the option panels from this list, the Worker validates with it,
// and the build scripts read the result from config.features.<id>.
//
// Field types: bool, text, textarea, color (empty = theme colour), int, select, url, hosts, items, json.

const HEX = /^#[0-9a-fA-F]{6}$/;
const HOST = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$/;

function str(v, max) {
  return String(v ?? '').replace(/[\x00-\x1f\x7f\u{2028}\u{2029}]/gu, ' ').trim().slice(0, max);
}

/** Icons available for tabs, shortcuts and the floating button (Material Symbols names). */
export const NAV_ICONS = ['home', 'search', 'shopping_cart', 'person', 'favorite', 'notifications', 'menu',
  'chat', 'info', 'star', 'settings', 'call', 'mail', 'share', 'bookmark', 'location_on', 'grid_view', 'play_circle'];

const ICON_OPTIONS = NAV_ICONS.map((v) => [v, v.replace(/_/g, ' ')]);
const iconField = (def) => ({ key: 'icon', type: 'select', label: 'Icon', default: def, icons: true, options: ICON_OPTIONS });

export const FEATURE_GROUPS = ['Engage', 'Look', 'Code', 'Privacy', 'Behaviour'];

export const FEATURES = [
  // ------------------------------------------------------------ Engage
  {
    id: 'push', icon: 'notifications_active', group: 'Engage', platforms: ['android'],
    title: 'Push notifications',
    desc: 'Send notifications to everyone who installed the app, with Firebase Cloud Messaging.',
    fields: [
      { key: 'googleServices', type: 'json', label: 'google-services.json', required: true, max: 60000, accept: '.json,application/json',
        help: 'Firebase console → Project settings → Your apps → Add app → Android, with exactly this app\'s package id. Download google-services.json and upload it here.' },
      { key: 'topic', type: 'text', label: 'Topic every device joins', default: 'all', max: 60, pattern: '^[a-zA-Z0-9_.~%-]{1,60}$',
        help: 'In Firebase → Messaging, send to this topic to reach every user.' },
      { key: 'channelName', type: 'text', label: 'Notification channel name', default: 'General', max: 40 },
      { key: 'openUrl', type: 'bool', label: 'Open the "url" data field when a notification is tapped', default: true },
    ],
  },
  {
    id: 'reminder', icon: 'alarm', group: 'Engage', platforms: ['android', 'ios'],
    title: 'Daily reminder',
    desc: 'A local notification every day at a set time. No server needed.',
    fields: [
      { key: 'time', type: 'time', label: 'Time', default: '19:00' },
      { key: 'title', type: 'text', label: 'Title', default: 'We miss you', max: 60, required: true },
      { key: 'text', type: 'text', label: 'Message', default: 'Open the app to see what is new.', max: 140, required: true },
    ],
  },
  {
    id: 'rateApp', icon: 'star_rate', group: 'Engage', platforms: ['android', 'ios'],
    title: 'Rate-the-app prompt',
    desc: 'Asks users for a store rating after a number of launches.',
    fields: [
      { key: 'launches', type: 'int', label: 'Ask after this many launches', default: 5, min: 2, max: 50 },
      { key: 'title', type: 'text', label: 'Title', default: 'Enjoying the app?', max: 50 },
      { key: 'message', type: 'text', label: 'Message', default: 'A quick rating helps us a lot.', max: 140 },
    ],
  },
  {
    id: 'updatePrompt', icon: 'system_update', group: 'Engage', platforms: ['android', 'ios'],
    title: 'Update prompt',
    desc: 'Reads a small JSON file and asks users to update when a newer version is out.',
    fields: [
      { key: 'checkUrl', type: 'url', label: 'Version file URL', required: true,
        help: 'A JSON file on your site like {"versionCode": 2, "message": "New features!"}' },
      { key: 'force', type: 'bool', label: 'Block the app until it is updated', default: false },
      { key: 'message', type: 'text', label: 'Default message', default: 'A new version is available.', max: 140 },
    ],
  },
  {
    id: 'admob', icon: 'ad_units', group: 'Engage', platforms: ['android'],
    title: 'AdMob ads',
    desc: 'Earn money with a banner and optional full-screen ads from Google AdMob.',
    fields: [
      { key: 'appId', type: 'text', label: 'AdMob app id', required: true, max: 60, pattern: '^ca-app-pub-\\d{10,20}~\\d{6,12}$',
        placeholder: 'ca-app-pub-1234567890123456~1234567890' },
      { key: 'bannerId', type: 'text', label: 'Banner ad unit id', max: 60, pattern: '^(ca-app-pub-\\d{10,20}/\\d{6,12})?$',
        placeholder: 'ca-app-pub-1234567890123456/1234567890' },
      { key: 'bannerPosition', type: 'select', label: 'Banner position', default: 'bottom', options: [['bottom', 'Bottom'], ['top', 'Top']] },
      { key: 'interstitialId', type: 'text', label: 'Full-screen ad unit id', max: 60, pattern: '^(ca-app-pub-\\d{10,20}/\\d{6,12})?$' },
      { key: 'interstitialEvery', type: 'int', label: 'Full-screen ad every N page loads', default: 6, min: 2, max: 50 },
    ],
  },
  // ------------------------------------------------------------ Look
  {
    id: 'splash', icon: 'wallpaper', group: 'Look', platforms: ['android', 'ios'],
    title: 'Splash screen',
    desc: 'Shows while the first page loads: your colour, a full-screen image or a short video, with your logo on top.',
    fields: [
      { key: 'background', type: 'select', label: 'Background', default: 'color',
        options: [['color', 'Splash colour (set in the Look step)'], ['image', 'Image'], ['video', 'Short video']] },
      { key: 'image', type: 'media', kind: 'image', label: 'Splash image', showIf: ['background', 'image'],
        mimes: ['image/png', 'image/jpeg', 'image/webp'], maxBytes: 1_500_000,
        help: 'Portrait 1080 × 1920 px (9:16). PNG, JPG or WebP, up to 1.5 MB. Keep text and logos in the middle: phones with other shapes trim the edges.' },
      { key: 'video', type: 'media', kind: 'video', label: 'Splash video', showIf: ['background', 'video'],
        mimes: ['video/mp4'], maxBytes: 4_000_000, maxSeconds: 8,
        help: 'MP4 (H.264), portrait 1080 × 1920 px (9:16), up to 8 seconds and 4 MB. Plays once, without sound; the app opens when it ends.' },
      { key: 'fit', type: 'select', label: 'Fit', default: 'cover', showIf: ['background', 'image', 'video'],
        options: [['cover', 'Fill the screen (edges may be trimmed)'], ['contain', 'Show all of it (splash colour around it)']] },
      { key: 'style', type: 'select', label: 'On top', default: 'logo_name',
        options: [['logo_name', 'Logo and app name'], ['logo', 'Logo only'], ['name', 'App name only'], ['none', 'Nothing']] },
      { key: 'minMs', type: 'int', label: 'Show at least (milliseconds)', default: 600, min: 0, max: 5000 },
      { key: 'maxMs', type: 'int', label: 'Hide after at most (milliseconds)', default: 4000, min: 1000, max: 10000 },
      { key: 'textColor', type: 'color', label: 'Name colour', default: '' },
    ],
  },
  {
    id: 'loader', icon: 'progress_activity', group: 'Look', platforms: ['android', 'ios'],
    title: 'Loading indicator',
    desc: 'Shows page loading as a thin bar or a spinner, in your colour.',
    fields: [
      { key: 'style', type: 'select', label: 'Style', default: 'bar', options: [['bar', 'Top bar'], ['spinner', 'Spinner'], ['none', 'Hidden']] },
      { key: 'color', type: 'color', label: 'Colour', default: '' },
    ],
  },
  {
    id: 'topBar', icon: 'web_asset', group: 'Look', platforms: ['android', 'ios'],
    title: 'Top app bar',
    desc: 'A native header with the title and handy buttons.',
    fields: [
      { key: 'title', type: 'select', label: 'Title shows', default: 'app', options: [['app', 'App name'], ['page', 'Page title']] },
      { key: 'back', type: 'bool', label: 'Back button', default: true },
      { key: 'home', type: 'bool', label: 'Home button', default: false },
      { key: 'refresh', type: 'bool', label: 'Refresh button', default: true },
      { key: 'share', type: 'bool', label: 'Share button', default: false },
      { key: 'bg', type: 'color', label: 'Background', default: '' },
      { key: 'fg', type: 'color', label: 'Text and icons', default: '#FFFFFF' },
    ],
  },
  {
    id: 'bottomNav', icon: 'bottom_navigation', group: 'Look', platforms: ['android', 'ios'],
    title: 'Bottom navigation',
    desc: 'Up to 5 tabs at the bottom that open the pages you choose.',
    fields: [
      { key: 'items', type: 'items', label: 'Tabs', min: 2, max: 5, add: 'Add tab', default: [],
        fields: [{ key: 'label', type: 'text', label: 'Label', max: 16, required: true },
          { key: 'url', type: 'url', label: 'Link', required: true }, iconField('home')] },
      { key: 'bg', type: 'color', label: 'Background', default: '#FFFFFF' },
      { key: 'active', type: 'color', label: 'Active tab colour', default: '' },
    ],
  },
  {
    id: 'fab', icon: 'add_circle', group: 'Look', platforms: ['android', 'ios'],
    title: 'Floating button',
    desc: 'A round button for WhatsApp, a call, email, a link or sharing.',
    fields: [
      { key: 'action', type: 'select', label: 'Action', default: 'whatsapp',
        options: [['whatsapp', 'WhatsApp chat'], ['tel', 'Phone call'], ['email', 'Email'], ['url', 'Open a link'], ['share', 'Share the page']] },
      { key: 'value', type: 'text', label: 'Number, email or link', max: 300,
        help: 'WhatsApp and phone: the number with country code, like +1 555 123 4567 (country code first).' },
      { key: 'icon', type: 'select', label: 'Icon', default: 'chat', icons: true, options: ICON_OPTIONS },
      { key: 'color', type: 'color', label: 'Colour', default: '' },
      { key: 'position', type: 'select', label: 'Position', default: 'right', options: [['right', 'Bottom right'], ['left', 'Bottom left']] },
    ],
  },
  {
    id: 'onboarding', icon: 'view_carousel', group: 'Look', platforms: ['android', 'ios'],
    title: 'Welcome slides',
    desc: 'Up to 4 intro slides the first time the app opens.',
    fields: [
      { key: 'slides', type: 'items', label: 'Slides', min: 1, max: 4, add: 'Add slide', default: [],
        fields: [{ key: 'title', type: 'text', label: 'Title', max: 40, required: true }, { key: 'text', type: 'text', label: 'Text', max: 160 }] },
      { key: 'button', type: 'text', label: 'Last button', default: 'Get started', max: 24 },
      { key: 'bg', type: 'color', label: 'Background', default: '' },
    ],
  },
  {
    id: 'systemBars', icon: 'splitscreen_top', group: 'Look', platforms: ['android', 'ios'],
    title: 'Status and navigation bars',
    desc: 'Colours of the phone status bar and navigation bar.',
    fields: [
      { key: 'status', type: 'color', label: 'Status bar colour', default: '' },
      { key: 'icons', type: 'select', label: 'Status bar icons', default: 'auto', options: [['auto', 'Automatic'], ['light', 'Light'], ['dark', 'Dark']] },
      { key: 'nav', type: 'color', label: 'Navigation bar colour (Android)', default: '' },
    ],
  },
  {
    id: 'darkMode', icon: 'dark_mode', group: 'Look', platforms: ['android', 'ios'],
    title: 'Dark mode',
    desc: 'Follows the phone\'s dark mode; can darken pages that have no dark design.',
    fields: [{ key: 'darkenPages', type: 'bool', label: 'Darken pages without a dark theme (Android 13+)', default: true }],
  },
  {
    id: 'textSize', icon: 'format_size', group: 'Look', platforms: ['android', 'ios'],
    title: 'Fixed text size',
    desc: 'Keeps page text at your size, whatever the phone\'s font setting.',
    fields: [{ key: 'percent', type: 'int', label: 'Text size (%)', default: 100, min: 50, max: 200 }],
  },
  // ------------------------------------------------------------ Code
  {
    id: 'customCss', icon: 'css', group: 'Code', platforms: ['android', 'ios'],
    title: 'Custom CSS',
    desc: 'Restyle the website inside the app, for example hide its header or footer.',
    fields: [{ key: 'css', type: 'textarea', label: 'CSS', max: 20000, required: true, placeholder: '.site-footer { display: none; }' }],
  },
  {
    id: 'customJs', icon: 'javascript', group: 'Code', platforms: ['android', 'ios'],
    title: 'Custom JavaScript',
    desc: 'Runs your script on every page after it loads.',
    fields: [{ key: 'js', type: 'textarea', label: 'JavaScript', max: 20000, required: true, placeholder: 'document.body.classList.add("in-app");' }],
  },
  {
    id: 'jsBridge', icon: 'integration_instructions', group: 'Code', platforms: ['android', 'ios'],
    title: 'JavaScript bridge',
    desc: 'Your website can call the phone: share, vibrate, copy, toast, print and more.',
    fields: [
      { key: 'name', type: 'text', label: 'Object name', default: 'Web2App', max: 30, pattern: '^[A-Za-z_$][A-Za-z0-9_$]{0,29}$',
        help: 'window.Web2App.share(text), .vibrate(ms), .toast(text), .copy(text), .print(), .appVersion(), .pushToken(), .openExternal(url)' },
    ],
  },
  {
    id: 'deepLinks', icon: 'link', group: 'Code', platforms: ['android', 'ios'],
    title: 'Deep links',
    desc: 'Links like myapp://shop/42 or your website links open straight in the app.',
    fields: [
      { key: 'scheme', type: 'text', label: 'Custom scheme', max: 30, pattern: '^([a-z][a-z0-9+.-]{1,29})?$', placeholder: 'myapp',
        help: 'myapp://shop/42 opens https://your-site/shop/42 inside the app.' },
      { key: 'hosts', type: 'hosts', label: 'Website hosts that open in the app (Android)', max: 10, placeholder: 'example.com' },
    ],
  },
  // ------------------------------------------------------------ Privacy
  {
    id: 'appLock', icon: 'fingerprint', group: 'Privacy', platforms: ['android', 'ios'],
    title: 'App lock',
    desc: 'Fingerprint, face or phone PIN before the app opens.',
    fields: [
      { key: 'relock', type: 'select', label: 'Lock again after leaving for', default: '1',
        options: [['0', 'Lock right away'], ['1', '1 minute'], ['5', '5 minutes'], ['30', '30 minutes'], ['-1', 'Only when the app starts']] },
      { key: 'title', type: 'text', label: 'Prompt title', default: 'Unlock', max: 40 },
    ],
  },
  {
    id: 'secureScreen', icon: 'screenshot_monitor', group: 'Privacy', platforms: ['android'],
    title: 'Block screenshots',
    desc: 'Stops screenshots and screen recording, and hides the app preview in recent apps.',
    fields: [],
  },
  {
    id: 'clearOnStart', icon: 'mop', group: 'Privacy', platforms: ['android', 'ios'],
    title: 'Clear data on start',
    desc: 'Starts fresh every time the app opens.',
    fields: [{ key: 'what', type: 'select', label: 'Clear', default: 'cache', options: [['cache', 'Cache only'], ['all', 'Cache, cookies and storage']] }],
  },
  {
    id: 'blocker', icon: 'block', group: 'Privacy', platforms: ['android', 'ios'],
    title: 'Content blocker',
    desc: 'Blocks requests to hosts you list, such as trackers or ad servers.',
    fields: [{ key: 'hosts', type: 'hosts', label: 'Blocked hosts', max: 50, required: true, placeholder: 'tracker.example.com' }],
  },
  // ------------------------------------------------------------ Behaviour
  {
    id: 'noSelect', icon: 'touch_app', group: 'Behaviour', platforms: ['android', 'ios'],
    title: 'Native feel',
    desc: 'Turns off text selection, long-press menus and tap highlights.',
    fields: [],
  },
  {
    id: 'rememberPage', icon: 'history', group: 'Behaviour', platforms: ['android', 'ios'],
    title: 'Remember last page',
    desc: 'Reopens the page the user was on instead of the home page.',
    fields: [],
  },
  {
    id: 'desktopMode', icon: 'desktop_windows', group: 'Behaviour', platforms: ['android', 'ios'],
    title: 'Desktop mode',
    desc: 'Asks the website for its desktop layout.',
    fields: [],
  },
  {
    id: 'swipeNav', icon: 'swipe', group: 'Behaviour', platforms: ['android', 'ios'],
    title: 'Swipe navigation',
    desc: 'Swipe in from the screen edge to go back or forward.',
    fields: [],
  },
  {
    id: 'exitConfirm', icon: 'logout', group: 'Behaviour', platforms: ['android'],
    title: 'Exit confirmation',
    desc: 'Stops the app closing by accident with the back button.',
    fields: [
      { key: 'mode', type: 'select', label: 'How', default: 'double', options: [['double', 'Press back twice'], ['dialog', 'Ask in a dialog']] },
      { key: 'message', type: 'text', label: 'Message', default: 'Press back again to exit', max: 60 },
    ],
  },
  {
    id: 'netBanner', icon: 'wifi_off', group: 'Behaviour', platforms: ['android', 'ios'],
    title: 'Connection banner',
    desc: 'A small banner when the connection drops or comes back.',
    fields: [
      { key: 'offline', type: 'text', label: 'Offline text', default: 'You are offline', max: 50 },
      { key: 'online', type: 'text', label: 'Back online text', default: 'Back online', max: 50 },
    ],
  },
  {
    id: 'shortcuts', icon: 'apps', group: 'Behaviour', platforms: ['android'],
    title: 'App shortcuts',
    desc: 'Long-press the app icon to jump straight to up to 4 pages.',
    fields: [
      { key: 'items', type: 'items', label: 'Shortcuts', min: 1, max: 4, add: 'Add shortcut', default: [],
        fields: [{ key: 'label', type: 'text', label: 'Label', max: 20, required: true },
          { key: 'url', type: 'url', label: 'Link', required: true }, iconField('star')] },
    ],
  },

  // ------------------------------------------------------------ added in 1.2
  {
    id: 'announcement', icon: 'campaign', group: 'Engage', platforms: ['android', 'ios'], added: '1.2',
    title: 'Announcement popup',
    desc: 'A one-time message when the app opens, such as news or an offer. Shown again only when you raise its number.',
    fields: [
      { key: 'title', type: 'text', label: 'Title', default: 'What\'s new', max: 50, required: true },
      { key: 'message', type: 'textarea', label: 'Message', default: 'Thanks for installing the app!', max: 400, required: true },
      { key: 'button', type: 'text', label: 'Close button', default: 'Got it', max: 20 },
      { key: 'link', type: 'url', label: 'Link (optional)', help: 'Adds a second button that opens this page.' },
      { key: 'linkLabel', type: 'text', label: 'Link button', default: 'Learn more', max: 20 },
      { key: 'version', type: 'int', label: 'Announcement number', default: 1, min: 1, max: 9999,
        help: 'Raise this in a later app version to show a new announcement to everyone once.' },
    ],
  },
  {
    id: 'noticeBar', icon: 'newspaper', group: 'Engage', platforms: ['android', 'ios'], added: '1.2',
    title: 'Live notice bar',
    desc: 'A slim bar at the top with a message you change any time from your website, without a new app version.',
    fields: [
      { key: 'checkUrl', type: 'url', label: 'Notice file URL', required: true,
        help: 'A JSON file on your site like {"id": "sale1", "message": "50% off today", "link": "https://…"}. An empty message hides the bar.' },
      { key: 'color', type: 'color', label: 'Bar colour', default: '' },
      { key: 'dismissible', type: 'bool', label: 'Users can close it (it stays closed until the id changes)', default: true },
    ],
  },
  {
    id: 'drawer', icon: 'menu_open', group: 'Look', platforms: ['android', 'ios'], added: '1.2',
    title: 'Side menu',
    desc: 'A menu that slides in from the left with your most important pages.',
    fields: [
      { key: 'items', type: 'items', label: 'Menu links', min: 1, max: 10, add: 'Add link', default: [],
        fields: [{ key: 'label', type: 'text', label: 'Label', max: 30, required: true },
          { key: 'url', type: 'url', label: 'Link', required: true }, iconField('star')] },
      { key: 'header', type: 'bool', label: 'Show the app icon and name at the top', default: true },
      { key: 'headerColor', type: 'color', label: 'Header colour', default: '' },
    ],
  },
  {
    id: 'scrollTop', icon: 'vertical_align_top', group: 'Look', platforms: ['android', 'ios'], added: '1.2',
    title: 'Back-to-top button',
    desc: 'A small round button appears on long pages and scrolls smoothly back to the top.',
    fields: [
      { key: 'after', type: 'int', label: 'Show after scrolling (pixels)', default: 800, min: 200, max: 5000 },
      { key: 'position', type: 'select', label: 'Position', default: 'right', options: [['right', 'Right'], ['left', 'Left'], ['center', 'Centre']] },
    ],
  },
  {
    id: 'pageFade', icon: 'animation', group: 'Look', platforms: ['android', 'ios'], added: '1.2',
    title: 'Page transitions',
    desc: 'Pages fade in softly instead of jumping, like a native app.',
    fields: [
      { key: 'ms', type: 'int', label: 'Fade length (milliseconds)', default: 250, min: 100, max: 800 },
    ],
  },
  {
    id: 'autoTheme', icon: 'palette', group: 'Look', platforms: ['android', 'ios'], added: '1.2',
    title: 'Colours from each page',
    desc: 'The top bar and status bar take the colour each page asks for with <meta name="theme-color">.',
    fields: [
      { key: 'topBar', type: 'bool', label: 'Colour the top bar', default: true },
      { key: 'statusBar', type: 'bool', label: 'Colour the status bar', default: true },
    ],
  },
  {
    id: 'notifyBridge', icon: 'notification_add', group: 'Code', platforms: ['android', 'ios'], added: '1.2',
    title: 'Local notifications from the page',
    desc: 'Your page can schedule reminders on the phone, e.g. Web2AppNotify.schedule("Title", "Text", 3600, "https://…"). It gives back the reminder number (on iPhone as a Promise).',
    fields: [
      { key: 'channelName', type: 'text', label: 'Notification category name', default: 'Reminders', max: 40 },
      { key: 'max', type: 'int', label: 'At most this many waiting at once', default: 20, min: 1, max: 60 },
    ],
  },
  {
    id: 'privacyCover', icon: 'visibility_off', group: 'Privacy', platforms: ['android', 'ios'], added: '1.2',
    title: 'Hide in app switcher',
    desc: 'Covers the page in the recent apps screen, so private content is not visible there.',
    fields: [],
  },
  {
    id: 'consent', icon: 'policy', group: 'Privacy', platforms: ['android', 'ios'], added: '1.2',
    title: 'Privacy consent',
    desc: 'Users must accept your privacy policy before they can use the app.',
    fields: [
      { key: 'title', type: 'text', label: 'Title', default: 'Your privacy', max: 50, required: true },
      { key: 'message', type: 'textarea', label: 'Message', max: 600, required: true,
        default: 'We use cookies and similar data to run this app. Please read and accept our privacy policy to continue.' },
      { key: 'policyUrl', type: 'url', label: 'Privacy policy URL', required: true },
      { key: 'accept', type: 'text', label: 'Accept button', default: 'Accept', max: 20 },
      { key: 'version', type: 'int', label: 'Policy version', default: 1, min: 1, max: 9999,
        help: 'Raise it when the policy changes to ask everyone again.' },
    ],
  },
  {
    id: 'ageGate', icon: 'cake', group: 'Privacy', platforms: ['android', 'ios'], added: '1.2',
    title: 'Age check',
    desc: 'Asks users to confirm their age once before the app opens.',
    fields: [
      { key: 'age', type: 'int', label: 'Minimum age', default: 18, min: 12, max: 21 },
      { key: 'blocked', type: 'text', label: 'Message for younger users', default: 'Sorry, this app is not available for your age.', max: 120 },
    ],
  },
  {
    id: 'haptics', icon: 'ads_click', group: 'Behaviour', platforms: ['android', 'ios'], added: '1.2',
    title: 'Haptic feedback',
    desc: 'A light tap feel on the app\'s buttons and tabs, and optionally on links and buttons in the page.',
    fields: [
      { key: 'page', type: 'bool', label: 'Also on links and buttons inside the page', default: false },
    ],
  },
  {
    id: 'shake', icon: 'vibration', group: 'Behaviour', platforms: ['android', 'ios'], added: '1.2',
    title: 'Shake action',
    desc: 'Shake the phone to reload, go home or send feedback.',
    fields: [
      { key: 'action', type: 'select', label: 'When shaken', default: 'reload',
        options: [['reload', 'Reload the page'], ['home', 'Go to the home page'], ['feedback', 'Send feedback by email']] },
      { key: 'email', type: 'text', label: 'Feedback email', max: 120, pattern: '^([^@\\s]+@[^@\\s]+\\.[^@\\s]+)?$',
        placeholder: 'support@example.com' },
    ],
  },
  {
    id: 'idleReset', icon: 'timer', group: 'Behaviour', platforms: ['android', 'ios'], added: '1.2',
    title: 'Idle reset (kiosk)',
    desc: 'Goes back to the home page after nobody touched the screen for a while. Good for shop and event tablets.',
    fields: [
      { key: 'minutes', type: 'int', label: 'After this many minutes', default: 5, min: 1, max: 120 },
      { key: 'clear', type: 'bool', label: 'Also sign out (clear cookies and site data)', default: false },
    ],
  },
  {
    id: 'findInPage', icon: 'search', group: 'Behaviour', platforms: ['android', 'ios'], added: '1.2',
    title: 'Find in page',
    desc: 'A search button in the top bar finds words on the current page.',
    fields: [],
  },
  {
    id: 'printPage', icon: 'print', group: 'Behaviour', platforms: ['android', 'ios'], added: '1.2',
    title: 'Print and save as PDF',
    desc: 'A print button in the top bar prints the page or saves it as a PDF.',
    fields: [],
  },
  {
    id: 'offlineCache', icon: 'cloud_download', group: 'Behaviour', platforms: ['android', 'ios'], added: '1.2',
    title: 'Offline reading',
    desc: 'Without internet, pages the user opened before still show from the saved copy.',
    fields: [
      { key: 'message', type: 'text', label: 'Notice', default: 'You are offline. Showing a saved copy.', max: 80 },
    ],
  },
  {
    id: 'inAppBrowser', icon: 'open_in_browser', group: 'Behaviour', platforms: ['android', 'ios'], added: '1.2',
    title: 'In-app browser',
    desc: 'Links to other websites open in a browser sheet inside the app, so users never leave it.',
    fields: [
      { key: 'color', type: 'color', label: 'Toolbar colour', default: '' },
    ],
  },
  {
    id: 'longPress', icon: 'touch_app', group: 'Behaviour', platforms: ['android', 'ios'], added: '1.2',
    title: 'Long-press menu',
    desc: 'Long-press a link to open, copy or share it, or an image to save or share it.',
    fields: [
      { key: 'saveImages', type: 'bool', label: 'Offer "Save image"', default: true },
    ],
  },
  {
    id: 'brightness', icon: 'brightness_high', group: 'Behaviour', platforms: ['android', 'ios'], added: '1.2',
    title: 'Brightness boost',
    desc: 'Turns the screen brightness up on pages like tickets, QR codes or barcodes, and back down when leaving.',
    fields: [
      { key: 'paths', type: 'text', label: 'Pages (start of the path, comma separated)', required: true, max: 300,
        placeholder: '/ticket, /qr', pattern: '^\\s*/[^,\\s]*(\\s*,\\s*/[^,\\s]*)*\\s*$' },
      { key: 'level', type: 'int', label: 'Brightness (%)', default: 100, min: 50, max: 100 },
    ],
  },
  {
    id: 'backgroundAudio', icon: 'music_note', group: 'Behaviour', platforms: ['android', 'ios'], added: '1.2',
    title: 'Background audio',
    desc: 'Music, radio and podcasts keep playing when the app is closed or the screen is off.',
    fields: [],
  },
];

export const FEATURE_IDS = FEATURES.map((f) => f.id);

/** Default option values for a feature. */
export function featureDefaults(feature) {
  const out = {};
  for (const f of feature.fields) {
    out[f.key] = Array.isArray(f.default) ? [] : (f.default ?? (f.type === 'bool' ? false : f.type === 'hosts' ? [] : ''));
  }
  return out;
}

function checkField(f, v, where) {
  const label = `${where}: ${f.label}`;
  switch (f.type) {
    case 'bool':
      return { value: typeof v === 'boolean' ? v : !!f.default };
    case 'int': {
      const n = v === '' || v == null ? f.default : Number(v);
      if (!Number.isInteger(n) || n < f.min || n > f.max) return { error: `${label} must be a whole number from ${f.min} to ${f.max}` };
      return { value: n };
    }
    case 'select':
      return { value: f.options.some(([o]) => o === v) ? v : f.default };
    case 'time': {
      const s = str(v, 5) || f.default;
      if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(s)) return { error: `${label}: use HH:MM, like 19:00` };
      return { value: s };
    }
    case 'color': {
      const s = str(v, 7);
      if (!s) return { value: f.default || '' };
      if (!HEX.test(s)) return { error: `${label}: use a colour like #1565C0` };
      return { value: s.toUpperCase() };
    }
    case 'text':
    case 'textarea': {
      const raw = f.type === 'textarea' ? String(v ?? '').replace(/\u0000/g, '') : str(v, f.max + 1);
      if (raw.length > f.max) return { error: `${label} is too long (at most ${f.max} characters)` };
      const value = raw.trim() ? raw : (f.default ?? '');
      if (f.required && !String(value).trim()) return { error: `${label} is required` };
      if (f.pattern && value && !new RegExp(f.pattern).test(value)) return { error: `${label} is not in the right format` };
      return { value };
    }
    case 'url': {
      const s = str(v, 2000);
      if (!s) return f.required ? { error: `${label} is required` } : { value: '' };
      try {
        const u = new URL(s);
        if (u.protocol !== 'https:' && u.protocol !== 'http:') throw new Error('scheme');
        return { value: u.toString() };
      } catch {
        return { error: `${label} must be a full link starting with https://` };
      }
    }
    case 'hosts': {
      const list = (Array.isArray(v) ? v : String(v ?? '').split(/[\s,]+/))
        .map((h) => str(h, 200).toLowerCase().replace(/^https?:\/\//, '').replace(/[/:].*$/, ''))
        .filter(Boolean);
      const bad = list.find((h) => !HOST.test(h));
      if (bad) return { error: `${label}: "${bad}" is not a host name like example.com` };
      const value = [...new Set(list)].slice(0, f.max);
      if (f.required && !value.length) return { error: `${label}: add at least one` };
      return { value };
    }
    case 'items': {
      const list = Array.isArray(v) ? v.slice(0, f.max) : [];
      const value = [];
      for (let i = 0; i < list.length; i++) {
        const item = {};
        for (const sub of f.fields) {
          const r = checkField(sub, list[i]?.[sub.key], `${label} ${i + 1}`);
          if (r.error) return r;
          item[sub.key] = r.value;
        }
        value.push(item);
      }
      if (value.length < (f.min || 0)) return { error: `${label}: add at least ${f.min}` };
      return { value };
    }
    case 'media': {
      // The file itself travels next to the config (payload.media); the config keeps its type.
      const s = str(v, 40);
      if (!s) return { value: '' };
      if (!f.mimes.includes(s)) return { error: `${label}: this file type cannot be used` };
      return { value: s };
    }
    case 'json': {
      const s = typeof v === 'string' ? v : '';
      if (!s.trim()) return f.required ? { error: `${label}: upload the file` } : { value: '' };
      if (s.length > f.max) return { error: `${label} is too large` };
      try {
        JSON.parse(s);
      } catch {
        return { error: `${label} is not a valid JSON file` };
      }
      return { value: s };
    }
    default:
      return { value: undefined };
  }
}

/** Checks a google-services.json belongs to this package id. Returns an error text or null. */
export function checkGoogleServices(text, packageId) {
  let data;
  try {
    data = JSON.parse(text);
  } catch {
    return 'google-services.json is not valid JSON';
  }
  if (!data?.project_info?.project_id || !Array.isArray(data.client)) {
    return 'this is not a google-services.json file from Firebase';
  }
  const names = data.client.map((c) => c?.client_info?.android_client_info?.package_name).filter(Boolean);
  if (!names.includes(packageId)) {
    return `google-services.json has no Android app with the package id ${packageId}. ` +
      'In Firebase add an Android app with exactly that id, then download the file again.' +
      (names.length ? ` (The file has: ${names.slice(0, 3).join(', ')})` : '');
  }
  return null;
}

/** Validates config.features. Returns { ok: true, features } or { ok: false, error }. */
export function validateFeatures(input, packageId) {
  const src = input && typeof input === 'object' ? input : {};
  const features = {};
  for (const feature of FEATURES) {
    const raw = src[feature.id];
    if (!raw || typeof raw !== 'object' || raw.enabled !== true) continue;
    const out = { enabled: true };
    for (const f of feature.fields) {
      const r = checkField(f, raw[f.key], feature.title);
      if (r.error) return { ok: false, error: r.error };
      out[f.key] = r.value;
    }
    if (feature.id === 'push') {
      const err = checkGoogleServices(out.googleServices, packageId);
      if (err) return { ok: false, error: `Push notifications: ${err}` };
    }
    if (feature.id === 'fab' && out.action !== 'share' && !out.value) {
      return { ok: false, error: 'Floating button: enter the number, email or link' };
    }
    if (feature.id === 'admob' && !out.bannerId && !out.interstitialId) {
      return { ok: false, error: 'AdMob ads: enter a banner or a full-screen ad unit id' };
    }
    if (feature.id === 'deepLinks' && !out.scheme && !out.hosts.length) {
      return { ok: false, error: 'Deep links: enter a custom scheme or at least one host' };
    }
    if (feature.id === 'shake' && out.action === 'feedback' && !out.email) {
      return { ok: false, error: 'Shake action: enter the feedback email' };
    }
    if ((feature.id === 'findInPage' || feature.id === 'printPage') && !(src.topBar && src.topBar.enabled === true)) {
      return { ok: false, error: `${feature.title}: turn on the Top bar too, the button lives there` };
    }
    if (feature.id === 'splash' && out.maxMs < out.minMs) {
      return { ok: false, error: 'Splash screen: "at most" must be larger than "at least"' };
    }
    if (feature.id === 'splash' && out.background === 'image' && !out.image) {
      return { ok: false, error: 'Splash screen: add the splash image' };
    }
    if (feature.id === 'splash' && out.background === 'video' && !out.video) {
      return { ok: false, error: 'Splash screen: add the splash video' };
    }
    features[feature.id] = out;
  }
  return { ok: true, features };
}
