// plugins/common/cheerio.js
// Pure JavaScript lightweight Cheerio/DOM implementation for QuickJS-NG without Node.js

function parseAttributes(attrString) {
  const attrs = {};
  const regex = /([a-zA-Z0-9_-]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?/g;
  let match;
  while ((match = regex.exec(attrString)) !== null) {
    const key = match[1];
    const val = match[2] !== undefined ? match[2] : (match[3] !== undefined ? match[3] : (match[4] !== undefined ? match[4] : ""));
    attrs[key.toLowerCase()] = val;
  }
  return attrs;
}

function parseHTML(html) {
  const nodes = [];
  // Match elements
  const tagRegex = /<([a-zA-Z0-9_-]+)([^>]*)>([\s\S]*?)<\/\1>|<([a-zA-Z0-9_-]+)([^>]*?)\/?>/g;
  let match;
  while ((match = tagRegex.exec(html)) !== null) {
    const tagName = (match[1] || match[4] || "").toLowerCase();
    const attrStr = match[2] || match[5] || "";
    const innerHtml = match[3] || "";
    const attrs = parseAttributes(attrStr);

    nodes.push({
      tagName,
      attrs,
      innerHtml,
      raw: match[0],
      text: innerHtml.replace(/<[^>]+>/g, '').trim()
    });
  }
  return nodes;
}

class CheerioElement {
  constructor(node) {
    this.node = node || { tagName: '', attrs: {}, innerHtml: '', text: '' };
  }

  attr(name) {
    if (!name) return undefined;
    return this.node.attrs[name.toLowerCase()];
  }

  text() {
    return this.node.text || "";
  }

  html() {
    return this.node.innerHtml || "";
  }

  find(selector) {
    return select(this.node.innerHtml || this.node.raw || "", selector);
  }

  closest(selector) {
    return new CheerioCollection([this.node]);
  }
}

class CheerioCollection {
  constructor(nodes = []) {
    this.nodes = nodes;
    this.length = nodes.length;
  }

  each(callback) {
    for (let i = 0; i < this.nodes.length; i++) {
      callback(i, new CheerioElement(this.nodes[i]));
    }
    return this;
  }

  map(callback) {
    const res = [];
    for (let i = 0; i < this.nodes.length; i++) {
      res.push(callback(i, new CheerioElement(this.nodes[i])));
    }
    return {
      get: () => res
    };
  }

  first() {
    return new CheerioElement(this.nodes[0]);
  }

  attr(name) {
    if (this.nodes.length === 0) return undefined;
    return new CheerioElement(this.nodes[0]).attr(name);
  }

  text() {
    return this.nodes.map(n => n.text).join(' ').trim();
  }

  find(selector) {
    const found = [];
    for (const n of this.nodes) {
      const sub = select(n.innerHtml || n.raw || "", selector);
      found.push(...sub.nodes);
    }
    return new CheerioCollection(found);
  }
}

function matchesSelector(node, sel) {
  sel = sel.trim();
  if (!sel) return false;

  // 1. Tag selector: "a", "article", "h1", "meta"
  if (/^[a-zA-Z0-9_-]+$/.test(sel)) {
    return node.tagName === sel.toLowerCase();
  }

  // 2. Class selector: ".video-item"
  if (sel.startsWith('.')) {
    const className = sel.substring(1);
    const nodeClass = node.attrs['class'] || '';
    return nodeClass.split(/\s+/).includes(className);
  }

  // 3. Tag with class: "h1.page-title"
  if (/^[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+$/.test(sel)) {
    const [tag, cls] = sel.split('.');
    return node.tagName === tag.toLowerCase() && (node.attrs['class'] || '').split(/\s+/).includes(cls);
  }

  // 4. Attribute selector: [attr*="val"], [attr="val"], [attr]
  const attrMatch = sel.match(/^([a-zA-Z0-9_-]*)\[([a-zA-Z0-9_-]+)(?:([*~|^$]?=)(?:"([^"]*)"|'([^']*)'|([^\]]+)))?\]$/);
  if (attrMatch) {
    const tag = attrMatch[1];
    const attrName = attrMatch[2].toLowerCase();
    const op = attrMatch[3] || "";
    const expectedVal = attrMatch[4] !== undefined ? attrMatch[4] : (attrMatch[5] !== undefined ? attrMatch[5] : (attrMatch[6] || ""));

    if (tag && node.tagName !== tag.toLowerCase()) return false;
    const actualVal = node.attrs[attrName];
    if (actualVal === undefined) return false;
    if (!op) return true;
    if (op === '=') return actualVal === expectedVal;
    if (op === '*=') return actualVal.includes(expectedVal);
    if (op === '^=') return actualVal.startsWith(expectedVal);
    if (op === '$=') return actualVal.endsWith(expectedVal);
  }

  return false;
}

function select(html, selector) {
  if (!html || !selector) return new CheerioCollection([]);
  const selectors = selector.split(',').map(s => s.trim());
  const allNodes = parseHTML(html);

  const matched = [];
  for (const node of allNodes) {
    for (const sel of selectors) {
      if (matchesSelector(node, sel)) {
        matched.push(node);
        break;
      }
    }
  }

  return new CheerioCollection(matched);
}

export function load(html) {
  const root = function(selector) {
    if (typeof selector === 'object' && selector.node) {
      return selector;
    }
    return select(html, selector);
  };
  return root;
}

export default { load };

