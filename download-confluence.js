const https = require('https');
const fs = require('fs');
const path = require('path');
const assert = require('assert');

const BLOCKED_PAGE_TITLES = new Set([
  'Developer Mode',
  'Notes | FAQ',
  'Building a JavaFX Application and NSIS Installer Steps'
]);

function isBlockedPage(title) {
  return BLOCKED_PAGE_TITLES.has(title.trim());
}

if (process.argv[2] === '--self-test') {
  assert(isBlockedPage('Developer Mode'));
  assert(isBlockedPage(' Notes | FAQ '));
  assert(!isBlockedPage('DCAM Android Development Standard'));
  console.log('Blacklist self-test passed.');
  process.exit(0);
}

function readProperties(filepath) {
  if (!fs.existsSync(filepath)) return {};

  return Object.fromEntries(
    fs.readFileSync(filepath, 'utf8')
      .split(/\r?\n/)
      .map(line => line.trim())
      .filter(line => line && !line.startsWith('#') && line.includes('='))
      .map(line => {
        const separator = line.indexOf('=');
        return [line.slice(0, separator).trim(), line.slice(separator + 1).trim()];
      })
  );
}

// Configuration
const localProperties = readProperties(path.join(__dirname, 'application-local.properties'));
const confluenceUrl = new URL(
  process.env.CONFLUENCE_BASE_URL
    || localProperties.CONFLUENCE_BASE_URL
    || 'https://ducviet.atlassian.net/wiki'
);
const CONFIG = {
  hostname: confluenceUrl.hostname,
  basePath: confluenceUrl.pathname.replace(/\/$/, ''),
  baseUrl: confluenceUrl.toString().replace(/\/$/, ''),
  email: process.env.ATLASSIAN_EMAIL || localProperties.ATLASSIAN_EMAIL,
  token: process.env.ATLASSIAN_TOKEN || localProperties.ATLASSIAN_TOKEN,
  spaceKey: 'DVID',
  outputDir: path.join(__dirname, 'docs', 'dcam-knowledge', 'confluence-original')
};

if (!CONFIG.email || !CONFIG.token) {
  throw new Error(
    'Missing ATLASSIAN_EMAIL or ATLASSIAN_TOKEN in the environment or application-local.properties'
  );
}

// Create auth header
const auth = Buffer.from(`${CONFIG.email}:${CONFIG.token}`).toString('base64');

// Helper: Make API request
function apiRequest(apiPath) {
  return new Promise((resolve, reject) => {
    const fullPath = CONFIG.basePath + apiPath;
    const options = {
      hostname: CONFIG.hostname,
      path: fullPath,
      method: 'GET',
      headers: {
        'Authorization': `Basic ${auth}`,
        'Accept': 'application/json'
      }
    };

    https.get(options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          try {
            resolve(JSON.parse(data));
          } catch (e) {
            reject(new Error(`Failed to parse JSON: ${e.message}`));
          }
        } else {
          reject(new Error(`HTTP ${res.statusCode}: ${data}`));
        }
      });
    }).on('error', reject);
  });
}

// Helper: Convert HTML to basic markdown
const HTML_ENTITIES = {
  apos: "'", nbsp: ' ',
  agrave: 'à', aacute: 'á', acirc: 'â', atilde: 'ã', auml: 'ä',
  egrave: 'è', eacute: 'é', ecirc: 'ê', euml: 'ë',
  igrave: 'ì', iacute: 'í', icirc: 'î', iuml: 'ï',
  ograve: 'ò', oacute: 'ó', ocirc: 'ô', otilde: 'õ', ouml: 'ö',
  ugrave: 'ù', uacute: 'ú', ucirc: 'û', uuml: 'ü',
  yacute: 'ý', yuml: 'ÿ', ccedil: 'ç',
  Agrave: 'À', Aacute: 'Á', Acirc: 'Â', Atilde: 'Ã', Auml: 'Ä',
  Egrave: 'È', Eacute: 'É', Ecirc: 'Ê', Euml: 'Ë',
  Igrave: 'Ì', Iacute: 'Í', Icirc: 'Î', Iuml: 'Ï',
  Ograve: 'Ò', Oacute: 'Ó', Ocirc: 'Ô', Otilde: 'Õ', Ouml: 'Ö',
  Ugrave: 'Ù', Uacute: 'Ú', Ucirc: 'Û', Uuml: 'Ü',
  Yacute: 'Ý', Ccedil: 'Ç',
  ndash: '–', mdash: '—', hellip: '…', bull: '•',
  laquo: '«', raquo: '»', lsquo: '‘', rsquo: '’', ldquo: '“', rdquo: '”',
  rarr: '→', larr: '←', harr: '↔', ne: '≠', le: '≤', ge: '≥'
};

function htmlToMarkdown(html) {
  if (!html) return '';
  
  let md = html
    // Headers
    .replace(/<h1[^>]*>(.*?)<\/h1>/gi, '# $1\n\n')
    .replace(/<h2[^>]*>(.*?)<\/h2>/gi, '## $1\n\n')
    .replace(/<h3[^>]*>(.*?)<\/h3>/gi, '### $1\n\n')
    .replace(/<h4[^>]*>(.*?)<\/h4>/gi, '#### $1\n\n')
    .replace(/<h5[^>]*>(.*?)<\/h5>/gi, '##### $1\n\n')
    .replace(/<h6[^>]*>(.*?)<\/h6>/gi, '###### $1\n\n')
    // Paragraphs
    .replace(/<p[^>]*>(.*?)<\/p>/gi, '$1\n\n')
    // Bold and italic
    .replace(/<strong[^>]*>(.*?)<\/strong>/gi, '**$1**')
    .replace(/<b[^>]*>(.*?)<\/b>/gi, '**$1**')
    .replace(/<em[^>]*>(.*?)<\/em>/gi, '*$1*')
    .replace(/<i[^>]*>(.*?)<\/i>/gi, '*$1*')
    // Links
    .replace(/<a[^>]*href="([^"]*)"[^>]*>(.*?)<\/a>/gi, '[$2]($1)')
    // Lists
    .replace(/<ul[^>]*>/gi, '\n')
    .replace(/<\/ul>/gi, '\n')
    .replace(/<ol[^>]*>/gi, '\n')
    .replace(/<\/ol>/gi, '\n')
    .replace(/<li[^>]*>(.*?)<\/li>/gi, '- $1\n')
    // Code
    .replace(/<code[^>]*>(.*?)<\/code>/gi, '`$1`')
    .replace(/<pre[^>]*>(.*?)<\/pre>/gi, '```\n$1\n```\n')
    // Tables - basic conversion
    .replace(/<table[^>]*>/gi, '\n')
    .replace(/<\/table>/gi, '\n')
    .replace(/<tr[^>]*>/gi, '')
    .replace(/<\/tr>/gi, '\n')
    .replace(/<th[^>]*>(.*?)<\/th>/gi, '| $1 ')
    .replace(/<td[^>]*>(.*?)<\/td>/gi, '| $1 ')
    // Line breaks
    .replace(/<br\s*\/?>/gi, '\n')
    // Remove remaining HTML tags
    .replace(/<[^>]+>/g, '')
    // Decode HTML entities
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&#(\d+);/g, (_, value) => String.fromCodePoint(Number(value)))
    .replace(/&#x([0-9a-f]+);/gi, (_, value) => String.fromCodePoint(parseInt(value, 16)))
    .replace(/&([a-z]+);/gi, (entity, name) => HTML_ENTITIES[name] ?? entity)
    // Clean up extra whitespace
    .replace(/\n\n\n+/g, '\n\n')
    .trim();
  
  return md;
}

// Helper: Sanitize filename
function sanitizeFilename(name) {
  return name
    .replace(/[<>:"/\\|?*]/g, '-')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .substring(0, 200);
}

const INCLUDED_PAGE_ROOT = 'DVID-SnT/Projects/DCAM';

function pagePath(page) {
  return [...(page.ancestors || []), page]
    .map(item => sanitizeFilename(item.title))
    .join('/');
}

function isDcamPage(page) {
  const candidate = pagePath(page);
  return candidate === INCLUDED_PAGE_ROOT || candidate.startsWith(`${INCLUDED_PAGE_ROOT}/`);
}

function relativeDcamPath(page) {
  const parts = pagePath(page).split('/');
  const parentParts = INCLUDED_PAGE_ROOT.split('/').slice(0, -1);
  return parts.slice(parentParts.length);
}

// Fetch all pages in the space
async function fetchAllPages() {
  console.log('Fetching all pages from Confluence...');
  const allPages = [];
  let start = 0;
  const limit = 100;
  
  while (true) {
    const data = await apiRequest(
      `/rest/api/content?spaceKey=${CONFIG.spaceKey}&expand=ancestors,version&limit=${limit}&start=${start}`
    );
    
    const results = data.results || [];
    allPages.push(...results);
    console.log(`  Fetched ${allPages.length} pages so far...`);
    
    if (results.length < limit) break;
    start += limit;
  }
  
  console.log(`Total pages found: ${allPages.length}`);
  return allPages;
}

// Fetch page content with body
async function fetchPageContent(pageId) {
  return await apiRequest(
    `/rest/api/content/${pageId}?expand=ancestors,body.storage,body.view,version`
  );
}

// Build hierarchy and save pages
async function downloadPages() {
  try {
    // Ensure output directory exists
    if (!fs.existsSync(CONFIG.outputDir)) {
      fs.mkdirSync(CONFIG.outputDir, { recursive: true });
    }
    
    // Fetch either one requested page or the complete space.
    const requestedPageId = process.argv[2];
    const fetchedPages = requestedPageId
      ? [await fetchPageContent(requestedPageId)]
      : await fetchAllPages();
    const pages = fetchedPages.filter(page => isDcamPage(page));
    console.log(`Skipped non-DCAM pages: ${fetchedPages.length - pages.length}`);
    
    // Build page map for hierarchy
    const pageMap = new Map();
    pages.forEach(page => {
      pageMap.set(page.id, {
        id: page.id,
        title: page.title,
        type: page.type,
        ancestors: page.ancestors || [],
        version: page.version?.number || 0,
        children: []
      });
    });
    
    // Build parent-child relationships
    pages.forEach(page => {
      const ancestors = page.ancestors || [];
      if (ancestors.length > 0) {
        const parentId = ancestors[ancestors.length - 1].id;
        const parent = pageMap.get(parentId);
        if (parent) {
          parent.children.push(page.id);
        }
      }
    });
    
    // Create index file
    const indexLines = ['# Confluence Original Documentation\n'];
    indexLines.push(`Downloaded: ${new Date().toISOString()}\n`);
    indexLines.push(`Total pages: ${pages.length}\n\n`);
    indexLines.push('## Page Structure\n\n');
    
    // Download each page
    let downloaded = 0;
    let blocked = 0;
    for (const page of pages) {
      try {
        // Build path relative to the DCAM project root
        const relativeParts = relativeDcamPath(page);
        const pathParts = relativeParts.length === 1 ? relativeParts : relativeParts.slice(0, -1);
        
        // Create directory structure
        let currentPath = CONFIG.outputDir;
        for (const part of pathParts) {
          currentPath = path.join(currentPath, part);
          if (!fs.existsSync(currentPath)) {
            fs.mkdirSync(currentPath, { recursive: true });
          }
        }
        
        // Save page content
        const filename = sanitizeFilename(relativeParts[relativeParts.length - 1]) + '.md';
        const filepath = path.join(currentPath, filename);

        if (isBlockedPage(page.title)) {
          if (fs.existsSync(filepath)) fs.unlinkSync(filepath);
          console.log(`Blocked credential-bearing page: ${page.title}`);
          blocked++;
          continue;
        }

        console.log(`Downloading [${downloaded + 1}/${pages.length}]: ${page.title}`);

        const fullPage = requestedPageId ? page : await fetchPageContent(page.id);
        const body = fullPage.body?.view?.value || fullPage.body?.storage?.value || '';
        const markdown = htmlToMarkdown(body);
        
        const content = [
          `# ${page.title}\n`,
          `**Page ID**: ${page.id}  `,
          `**Version**: ${page.version?.number || 0}  `,
          `**Type**: ${page.type}  `,
          `**URL**: ${CONFIG.baseUrl}/spaces/${CONFIG.spaceKey}/pages/${page.id}\n`,
          `---\n\n`,
          markdown
        ].join('\n');
        
        fs.writeFileSync(filepath, content, 'utf8');
        
        // Add to index
        const indent = '  '.repeat(pathParts.length);
        const relPath = path.relative(CONFIG.outputDir, filepath).replace(/\\/g, '/');
        indexLines.push(`${indent}- [${page.title}](${relPath})\n`);
        
        downloaded++;
        
        // Rate limiting
        await new Promise(resolve => setTimeout(resolve, 200));
        
      } catch (err) {
        console.error(`  Error downloading page ${page.title}: ${err.message}`);
      }
    }
    
    // A targeted refresh preserves the complete-space index.
    if (!requestedPageId) {
      indexLines[2] = 'Total pages: ' + downloaded + '\n\n';
      fs.writeFileSync(
        path.join(CONFIG.outputDir, 'INDEX.md'),
        indexLines.join(''),
        'utf8'
      );
    }
    
    console.log(`\nDownload complete! ${downloaded} pages saved to:`);
    console.log(CONFIG.outputDir);
    console.log(`Blocked pages: ${blocked}`);
    
  } catch (error) {
    console.error('Error:', error.message);
    process.exit(1);
  }
}

// Run
downloadPages();
