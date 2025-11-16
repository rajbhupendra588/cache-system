# How to View System Design Diagrams

The diagrams in `SYSTEM_DESIGN.md` are written in **Mermaid syntax** and render as actual visual diagrams in supported viewers.

## ✅ Where Diagrams Are Visible

### 1. GitHub/GitLab (Recommended)
- **GitHub:** Push to repository → View on GitHub → Diagrams render automatically
- **GitLab:** Push to repository → View on GitLab → Diagrams render automatically
- **Best option:** View the file directly on GitHub/GitLab web interface

### 2. VS Code Extensions
Install one of these extensions:
- **Markdown Preview Enhanced** (by Yiyi Wang)
  - Install: `ext install yzhang.markdown-all-in-one`
  - Then install: `ext install shd101wyy.markdown-preview-enhanced`
  - Open `SYSTEM_DESIGN.md` → Right-click → "Markdown Preview Enhanced: Open Preview"
  
- **Markdown Preview Mermaid Support** (by Matt Bierner)
  - Install: `ext install bierner.markdown-mermaid`
  - Open `SYSTEM_DESIGN.md` → Press `Cmd+Shift+V` (Mac) or `Ctrl+Shift+V` (Windows)

### 3. Online Mermaid Editor
1. Go to: https://mermaid.live/
2. Copy a Mermaid diagram code from `SYSTEM_DESIGN.md`
3. Paste into the editor
4. See the rendered diagram instantly

### 4. Documentation Sites
These platforms support Mermaid:
- **MkDocs** (with mermaid2 plugin)
- **Docusaurus** (built-in support)
- **GitBook** (built-in support)
- **Notion** (supports Mermaid)

## 🔧 Quick Test

To verify Mermaid is working, try this simple diagram:

```mermaid
graph LR
    A[Start] --> B[Process]
    B --> C[End]
```

If you see a diagram with boxes and arrows, Mermaid is working!

## 📝 Alternative: Generate Image Files

If you need actual PNG/SVG images, you can:

1. **Use Mermaid CLI:**
   ```bash
   npm install -g @mermaid-js/mermaid-cli
   mmdc -i SYSTEM_DESIGN.md -o diagrams/
   ```

2. **Use Online Tool:**
   - Go to https://mermaid.live/
   - Copy diagram code
   - Export as PNG/SVG

3. **Use VS Code Extension:**
   - Install "Markdown Preview Mermaid Support"
   - Right-click on diagram → "Export as PNG"

## 🎯 Recommended Solution

**For immediate viewing:**
1. Push code to GitHub
2. View `docs/architecture/SYSTEM_DESIGN.md` on GitHub web interface
3. All diagrams will render automatically

**For local development:**
1. Install VS Code extension: "Markdown Preview Enhanced"
2. Open `SYSTEM_DESIGN.md`
3. Use preview pane to see rendered diagrams

---

**Note:** The diagrams ARE actual diagrams - they just need a Mermaid-compatible viewer to render them. GitHub and GitLab are the easiest options.

