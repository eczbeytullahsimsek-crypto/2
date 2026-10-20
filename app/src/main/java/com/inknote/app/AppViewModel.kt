package com.inknote.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inknote.app.data.Folder
import com.inknote.app.data.Notebook
import com.inknote.app.data.NotebookKind
import com.inknote.app.data.PageTemplate
import com.inknote.app.data.PaperSize
import com.inknote.app.data.PdfDoc
import com.inknote.app.data.Repository
import com.inknote.app.data.Scheme
import com.inknote.app.data.StrokeData
import com.inknote.app.ink.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class Route { LIBRARY, CANVAS, PAGES, SEARCH, CLOUD, SETTINGS }

private class Edit(val page: Int, val before: List<StrokeData>, val after: List<StrokeData>)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)

    var route by mutableStateOf(Route.LIBRARY)
    val notebooks = mutableStateListOf<Notebook>()
    var openId by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    // --- Açık defterin sayfa verisi ---
    val pageData = mutableStateMapOf<Int, List<StrokeData>>()
    private val undoStack = ArrayList<Edit>()
    private val redoStack = ArrayList<Edit>()
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set

    // --- PDF ---
    var pdf by mutableStateOf<PdfDoc?>(null); private set
    var pdfAspects by mutableStateOf<List<Float>?>(null); private set
    var pdfLoading by mutableStateOf(false); private set

    // --- Araç durumu ---
    var tool by mutableStateOf(Tool.PEN)
    var colorIdx by mutableStateOf(0)
    var widthIdx by mutableStateOf(1)
    var stylusOnly by mutableStateOf(false)
    var activePage by mutableStateOf(0)
    var scrollRequest by mutableStateOf<Int?>(null)

    val inkColors = mutableStateListOf(0xFF1C1B1F.toInt(), 0xFF4F55C4.toInt(), 0xFF2E6B56.toInt(), 0xFFE8C857.toInt(), 0xFFBA1A1A.toInt())
    val widthsDp = listOf(1.4f, 2.4f, 4.4f)

    init {
        notebooks.addAll(repo.loadNotebooks())
    }

    val current: Notebook? get() = notebooks.firstOrNull { it.id == openId } ?: null

    // ============================================================ defter işlemleri

    private fun persist() { val snap = notebooks.toList(); viewModelScope.launch(Dispatchers.IO) { repo.saveNotebooks(snap) } }

    private fun update(id: String, f: (Notebook) -> Notebook) {
        val i = notebooks.indexOfFirst { it.id == id }
        if (i >= 0) { notebooks[i] = f(notebooks[i]); persist() }
    }

    fun createNotebook(title: String, goCanvas: Boolean = true): Notebook {
        val nb = Notebook(UUID.randomUUID().toString().take(8), title.ifBlank { "Yeni Defter" }, "Defter",
            "Yeni oluşturulan defter", NotebookKind.NOTEBOOK,
            listOf(Scheme.PRIMARY, Scheme.TERTIARY, Scheme.SECONDARY).random(), Folder.UNI, 1)
        notebooks.add(0, nb); persist()
        if (goCanvas) open(nb.id, Route.CANVAS)
        return nb
    }

    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            message = "PDF içe aktarılıyor…"
            val result = withContext(Dispatchers.IO) { runCatching { repo.importPdf(uri) } }
            result.onSuccess { nb ->
                notebooks.add(0, nb); persist(); message = "PDF eklendi: ${nb.title}"
                open(nb.id, Route.CANVAS)
            }.onFailure {
                message = when (it) {
                    is SecurityException -> "Bu PDF şifreli, açılamadı."
                    else -> "PDF açılamadı: ${it.message ?: "bozuk veya desteklenmeyen dosya"}"
                }
            }
        }
    }

    fun toggleStar(id: String) = update(id) { it.copy(starred = !it.starred) }
    fun trash(id: String) = update(id) { it.copy(trashed = true) }
    fun restore(id: String) = update(id) { it.copy(trashed = false) }
    fun deleteForever(id: String) {
        if (openId == id) closeNotebook()
        notebooks.removeAll { it.id == id }
        persist()
        viewModelScope.launch(Dispatchers.IO) { repo.deleteNotebookFiles(id) }
    }

    fun open(id: String, to: Route = Route.CANVAS) {
        if (openId != id) {
            closeNotebook()
            openId = id
            val nb = notebooks.firstOrNull { it.id == id }
            activePage = nb?.lastPage ?: 0
            scrollRequest = activePage.takeIf { it > 0 }
            if (nb?.kind == NotebookKind.PDF) openPdf(nb)
        }
        route = to
    }

    private fun openPdf(nb: Notebook) {
        pdfLoading = true
        viewModelScope.launch {
            val doc = withContext(Dispatchers.IO) { runCatching { PdfDoc(repo.pdfFile(nb.id)) }.getOrNull() }
            if (doc == null) { message = "PDF dosyası okunamadı."; pdfLoading = false; return@launch }
            if (openId != nb.id) { doc.close(); return@launch }
            pdf = doc
            pdfAspects = runCatching { doc.aspects() }.getOrNull()
            pdfLoading = false
        }
    }

    private fun closeNotebook() {
        pdf?.close(); pdf = null; pdfAspects = null; pdfLoading = false
        pageData.clear(); undoStack.clear(); redoStack.clear(); refreshUndo()
        openId = null
    }

    override fun onCleared() { pdf?.close(); super.onCleared() }

    // ============================================================ sayfa verisi

    fun ensurePage(page: Int) {
        val id = openId ?: return
        if (pageData.containsKey(page)) return
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { repo.loadPage(id, page) }
            if (openId == id && !pageData.containsKey(page)) pageData[page] = list
        }
    }

    fun commit(page: Int, before: List<StrokeData>, after: List<StrokeData>) {
        val id = openId ?: return
        pageData[page] = after
        undoStack.add(Edit(page, before, after))
        if (undoStack.size > 200) undoStack.removeAt(0)
        redoStack.clear(); refreshUndo()
        saveAsync(id, page, after)
        update(id) { it.copy(updatedAt = System.currentTimeMillis(), lastPage = page) }
    }

    fun undo() {
        val e = undoStack.removeLastOrNull() ?: return
        apply(e.page, e.before); redoStack.add(e); refreshUndo(); scrollRequest = e.page
    }

    fun redo() {
        val e = redoStack.removeLastOrNull() ?: return
        apply(e.page, e.after); undoStack.add(e); refreshUndo(); scrollRequest = e.page
    }

    private fun apply(page: Int, list: List<StrokeData>) {
        val id = openId ?: return
        pageData[page] = list
        saveAsync(id, page, list)
    }

    private fun saveAsync(id: String, page: Int, list: List<StrokeData>) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { repo.savePage(id, page, list) } }
    }

    private fun refreshUndo() { canUndo = undoStack.isNotEmpty(); canRedo = redoStack.isNotEmpty() }

    // ============================================================ sayfa yönetimi

    fun addPage() {
        val nb = current ?: return
        if (nb.kind == NotebookKind.PDF) { message = "PDF belgelerine sayfa eklenemez."; return }
        update(nb.id) { it.copy(pageCount = it.pageCount + 1, updatedAt = System.currentTimeMillis()) }
    }

    fun applyTemplate(pages: Set<Int>, t: PageTemplate) {
        val nb = current ?: return
        if (nb.kind == NotebookKind.PDF) { message = "PDF sayfalarına şablon uygulanamaz."; return }
        update(nb.id) { n -> n.copy(templates = n.templates + pages.associateWith { t }) }
    }

    fun setDefaultTemplate(t: PageTemplate) { current?.let { nb -> update(nb.id) { it.copy(defaultTemplate = t) } } }
    fun setPaperTone(i: Int) { current?.let { nb -> update(nb.id) { it.copy(paperTone = i) } } }
    fun setPaperSize(s: PaperSize) { current?.let { nb -> update(nb.id) { it.copy(paperSize = s) } } }
}
