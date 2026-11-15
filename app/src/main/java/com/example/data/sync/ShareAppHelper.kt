package com.example.data.sync

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.model.LitigationCase
import com.example.data.model.LitigationTask
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

object ShareAppHelper {

    private const val PREFS_NAME = "sd_cms_share_prefs"
    private const val KEY_CUSTOM_PUBLIC_URL = "custom_public_install_url"

    fun getPublicInstallUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_CUSTOM_PUBLIC_URL, "").orEmpty().trim()
        if (saved.isNotBlank()) return saved
        return context.getString(R.string.share_app_url).trim()
    }

    fun savePublicInstallUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_PUBLIC_URL, url.trim())
            .apply()
    }

    fun shareAndroidApk(context: Context) {
        runCatching {
            val sourceApk = File(context.applicationInfo.sourceDir)
            if (!sourceApk.exists()) {
                Toast.makeText(context, "APK file unavailable in this build", Toast.LENGTH_SHORT).show()
                return
            }
            val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
            val destApk = File(shareDir, "SD-CMS.apk")
            sourceApk.inputStream().use { input ->
                destApk.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destApk,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "SD-CMS — Android App Installer")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Install SD-CMS (Dilawar & Saqlain Case Management) on your Android device."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Send SD-CMS Android APK"))
        }.onFailure {
            Toast.makeText(context, "Unable to share Android APK", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Generates a self-contained single-file HTML5 Web App (SD-CMS-iOS-App.html) pre-loaded with
     * the SD-CMS logo, Light/Dark Charcoal theme, and current cases so iOS (iPhone/iPad Safari)
     * and desktop/Android browsers can run and edit cases offline without needing an App Store build.
     */
    fun sharePortableIosWebApp(
        context: Context,
        cases: List<LitigationCase>,
        tasks: List<LitigationTask>,
    ) {
        runCatching {
            val html = buildPortableWebAppHtml(context, cases, tasks)
            val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
            val htmlFile = File(shareDir, "SD-CMS-iOS-App.html")
            htmlFile.writeText(html, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                htmlFile,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/html"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "SD-CMS — iOS & Cross-Platform App")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Open SD-CMS-iOS-App.html in Safari on iPhone/iPad (or any browser). To host as a public link for everyone, upload this file to Netlify Drop / GitHub Pages and share the URL."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Send SD-CMS for iOS / Web"))
        }.onFailure {
            Toast.makeText(context, "Unable to export iOS web app package", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyInstallLink(context: Context): Boolean {
        val url = getPublicInstallUrl(context)
        if (url.isBlank()) {
            Toast.makeText(
                context,
                "Paste your hosted web/TestFlight link first, or send SD-CMS-iOS-App.html directly",
                Toast.LENGTH_LONG,
            ).show()
            return false
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("SD-CMS Install Link", url))
        Toast.makeText(context, "Install link copied", Toast.LENGTH_SHORT).show()
        return true
    }

    fun sharePublicInstallLink(context: Context) {
        val url = getPublicInstallUrl(context)
        if (url.isBlank()) {
            Toast.makeText(
                context,
                "Save a hosted public link first, or use Send iOS Web App / Send APK",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        val message = buildString {
            appendLine("SD-CMS — Dilawar & Saqlain Case Management")
            appendLine()
            appendLine("Open / Install on iOS & Android:")
            appendLine(url)
            appendLine()
            appendLine("On iPhone/iPad: Open in Safari -> Tap Share -> Add to Home Screen (named SD-CMS).")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "SD-CMS — Install on iOS & Android")
            putExtra(Intent.EXTRA_TEXT, message)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Share SD-CMS Install Link"))
        }.onFailure {
            Toast.makeText(context, "No share app available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun encodeLogoBase64(context: Context): String {
        return runCatching {
            val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.sd_cms_logo) ?: return ""
            val scaled = Bitmap.createScaledBitmap(bmp, 192, 192, true)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 95, baos)
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        }.getOrDefault("")
    }

    private fun casesToJson(cases: List<LitigationCase>): String {
        val arr = JSONArray()
        cases.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("caseNumber", c.caseNumber)
                put("title", c.title)
                put("courtName", c.courtName)
                put("courtRoom", c.courtRoom)
                put("stage", c.stage)
                put("status", c.status)
                put("priority", c.priority)
                put("nextHearingEpochMs", c.nextHearingEpochMs)
                put("filingDeadlineEpochMs", c.filingDeadlineEpochMs)
                put("hearingDurationMinutes", c.hearingDurationMinutes)
                put("googleKeepUrl", c.googleKeepUrl)
                put("notes", c.notes)
            })
        }
        return arr.toString().replace("</", "<\\/")
    }

    private fun tasksToJson(tasks: List<LitigationTask>): String {
        val arr = JSONArray()
        tasks.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("caseNumber", t.caseNumber)
                put("title", t.title)
                put("description", t.description)
                put("dueEpochMs", t.dueEpochMs)
                put("completed", t.completed)
            })
        }
        return arr.toString().replace("</", "<\\/")
    }

    internal fun buildPortableWebAppHtml(
        context: Context,
        cases: List<LitigationCase>,
        tasks: List<LitigationTask>,
    ): String {
        val logoB64 = encodeLogoBase64(context)
        val iconTag = if (logoB64.isNotBlank()) {
            """<link rel="apple-touch-icon" href="data:image/png;base64,$logoB64"><link rel="icon" type="image/png" href="data:image/png;base64,$logoB64">"""
        } else {
            ""
        }
        val logoImg = if (logoB64.isNotBlank()) {
            """<img src="data:image/png;base64,$logoB64" alt="SD-CMS Logo" style="width:36px;height:36px;border-radius:8px;vertical-align:middle;" />"""
        } else {
            ""
        }
        val casesJson = casesToJson(cases)
        val tasksJson = tasksToJson(tasks)

        return """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover" />
<meta name="apple-mobile-web-app-capable" content="yes" />
<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent" />
<meta name="apple-mobile-web-app-title" content="SD-CMS" />
<title>SD-CMS — Dilawar & Saqlain</title>
$iconTag
<style>
:root{--bg:#F6F6F7;--surface:#FFFFFF;--surface2:#ECEEEF;--ink:#16181C;--muted:#636872;--border:#DADCE0;--primary:#1E2024;--onPrimary:#FFFFFF;}
body.dark{--bg:#111214;--surface:#1A1C1F;--surface2:#25282D;--ink:#F1F2F4;--muted:#9EA5B0;--border:#343840;--primary:#F3F4F6;--onPrimary:#141518;}
*{box-sizing:border-box;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;}
body{margin:0;background:var(--bg);color:var(--ink);padding-bottom:76px;}
header{position:sticky;top:0;z-index:10;background:var(--bg);border-bottom:1px solid var(--border);padding:12px 16px;display:flex;align-items:center;justify-content:space-between;gap:12px;}
.brand{display:flex;align-items:center;gap:10px;}
.brand h1{margin:0;font-size:17px;letter-spacing:-0.01em;}
.brand p{margin:0;font-size:11px;color:var(--muted);}
button{cursor:pointer;border:1px solid var(--border);background:var(--surface);color:var(--ink);border-radius:10px;padding:8px 12px;font-size:13px;font-weight:600;}
button.primary{background:var(--primary);color:var(--onPrimary);border-color:var(--primary);}
main{max-width:860px;margin:0 auto;padding:16px;}
.stats{background:var(--primary);color:var(--onPrimary);border-radius:16px;padding:16px;margin-bottom:14px;display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:10px;}
.tabs{display:flex;gap:8px;margin-bottom:14px;flex-wrap:wrap;}
.tabs button.active{background:var(--primary);color:var(--onPrimary);border-color:var(--primary);}
.search{width:100%;padding:10px 12px;border-radius:10px;border:1px solid var(--border);background:var(--surface);color:var(--ink);margin-bottom:12px;font-size:14px;}
.card{border:1px solid var(--border);border-radius:14px;padding:14px;margin-bottom:10px;background:var(--surface);}
.card.alt{background:var(--surface2);}
.caseno{font-size:12px;color:var(--muted);font-weight:600;}
.casetitle{font-size:16px;font-weight:800;margin:4px 0;}
.meta{font-size:13px;color:var(--muted);}
.modal{position:fixed;inset:0;background:rgba(0,0,0,0.55);display:none;align-items:center;justify-content:center;padding:16px;z-index:50;}
.modal.open{display:flex;}
.modal-box{background:var(--surface);color:var(--ink);border-radius:16px;padding:18px;width:100%;max-width:480px;max-height:90vh;overflow:auto;}
.field{width:100%;padding:9px 10px;margin:6px 0 10px;border-radius:8px;border:1px solid var(--border);background:var(--bg);color:var(--ink);}
</style>
</head>
<body>
<header>
  <div class="brand">$logoImg<div><h1>SD-CMS</h1><p>Dilawar &amp; Saqlain • Case Management</p></div></div>
  <div style="display:flex;gap:8px;">
    <button onclick="toggleTheme()">Theme</button>
    <button class="primary" onclick="openEditor()">+ Case</button>
  </div>
</header>
<main>
  <div class="stats">
    <div><div style="font-size:11px;opacity:.75;">TOTAL CASES</div><div id="caseCount" style="font-size:26px;font-weight:800;">0</div></div>
    <div style="display:flex;gap:8px;flex-wrap:wrap;">
      <button onclick="exportIcs()">Export .ics</button>
      <button onclick="emailCauseList()">Cause List</button>
    </div>
  </div>
  <div class="tabs">
    <button id="t-cases" class="active" onclick="setTab('cases')">All Cases</button>
    <button id="t-daily" onclick="setTab('daily')">Daily Diary</button>
    <button id="t-week" onclick="setTab('week')">This Week</button>
    <button id="t-month" onclick="setTab('month')">This Month</button>
  </div>
  <input id="q" class="search" placeholder="Search case number, title, court..." oninput="render()" />
  <div id="list"></div>
</main>
<div id="modal" class="modal"><div class="modal-box">
  <h3 style="margin-top:0;">Edit Case</h3>
  <input id="f-id" type="hidden" />
  <label>Case No.</label><input id="f-no" class="field" />
  <label>Case Title *</label><input id="f-title" class="field" />
  <label>Court</label><input id="f-court" class="field" />
  <label>Hearing Date &amp; Time</label><input id="f-date" type="datetime-local" class="field" />
  <label>Notes</label><textarea id="f-notes" class="field" rows="3"></textarea>
  <div style="display:flex;justify-content:flex-end;gap:8px;">
    <button onclick="closeEditor()">Cancel</button>
    <button class="primary" onclick="saveCase()">Save</button>
  </div>
</div></div>
<script>
const KEY="sd_cms_portable_cases_v1";
const SEED_CASES=$casesJson;
const SEED_TASKS=$tasksJson;
let cases=JSON.parse(localStorage.getItem(KEY)||"null")||SEED_CASES;
let tab="cases";
function persist(){localStorage.setItem(KEY,JSON.stringify(cases));}
function toggleTheme(){document.body.classList.toggle("dark");}
function setTab(t){tab=t;document.querySelectorAll(".tabs button").forEach(b=>b.classList.remove("active"));document.getElementById("t-"+t).classList.add("active");render();}
function fmt(ms){if(!ms)return "No date";return new Date(ms).toLocaleString();}
function inRange(ms){
  if(!ms)return false;
  const now=new Date();
  const s=new Date(now.getFullYear(),now.getMonth(),now.getDate()).getTime();
  if(tab==="daily")return ms>=s&&ms<s+86400000;
  if(tab==="week"){const d=(now.getDay()+6)%7;const ws=s-d*86400000;return ms>=ws&&ms<ws+7*86400000;}
  if(tab==="month"){const ms0=new Date(now.getFullYear(),now.getMonth(),1).getTime();const me=new Date(now.getFullYear(),now.getMonth()+1,1).getTime();return ms>=ms0&&ms<me;}
  return true;
}
function render(){
  document.getElementById("caseCount").textContent=cases.length;
  const q=(document.getElementById("q").value||"").toLowerCase();
  const list=cases.filter(c=>(tab==="cases"||inRange(c.nextHearingEpochMs))&&(!q||(c.caseNumber+" "+c.title+" "+c.courtName).toLowerCase().includes(q)));
  const el=document.getElementById("list");
  if(!list.length){el.innerHTML='<div class="card">No cases matching this view.</div>';return;}
  el.innerHTML=list.map((c,i)=>'<div class="card '+(i%2===1?'alt':'')+'" onclick="openEditor(\''+c.id+'\')"><div class="caseno">'+(c.caseNumber||'')+'</div><div class="casetitle">'+(c.title||'Untitled')+'</div><div class="meta">'+(c.courtName?c.courtName+' • ':'')+fmt(c.nextHearingEpochMs)+'</div></div>').join("");
}
function openEditor(id){
  const c=cases.find(x=>x.id===id)||{id:"c_"+Date.now(),caseNumber:"",title:"",courtName:"",nextHearingEpochMs:Date.now(),notes:""};
  document.getElementById("f-id").value=c.id;
  document.getElementById("f-no").value=c.caseNumber||"";
  document.getElementById("f-title").value=c.title||"";
  document.getElementById("f-court").value=c.courtName||"";
  document.getElementById("f-notes").value=c.notes||"";
  if(c.nextHearingEpochMs){const d=new Date(c.nextHearingEpochMs-new Date().getTimezoneOffset()*60000);document.getElementById("f-date").value=d.toISOString().slice(0,16);}else{document.getElementById("f-date").value="";}
  document.getElementById("modal").classList.add("open");
}
function closeEditor(){document.getElementById("modal").classList.remove("open");}
function saveCase(){
  const id=document.getElementById("f-id").value;
  const dt=document.getElementById("f-date").value;
  const ms=dt?new Date(dt).getTime():0;
  const item={id,caseNumber:document.getElementById("f-no").value,title:document.getElementById("f-title").value,courtName:document.getElementById("f-court").value,nextHearingEpochMs:ms,notes:document.getElementById("f-notes").value,hearingDurationMinutes:45};
  const idx=cases.findIndex(x=>x.id===id);
  if(idx>=0)cases[idx]=Object.assign({},cases[idx],item);else cases.push(item);
  persist();closeEditor();render();
}
function exportIcs(){
  const lines=["BEGIN:VCALENDAR","VERSION:2.0","PRODID:-//SD-CMS//EN"];
  cases.filter(c=>c.nextHearingEpochMs>0).forEach(c=>{
    const f=d=>new Date(d).toISOString().replace(/[-:]/g,"").split(".")[0]+"Z";
    lines.push("BEGIN:VEVENT","UID:"+c.id+"@sd-cms.app","DTSTART:"+f(c.nextHearingEpochMs),"DTEND:"+f(c.nextHearingEpochMs+2700000),"SUMMARY:["+c.caseNumber+"] "+c.title,"LOCATION:"+(c.courtName||""),"END:VEVENT");
  });
  lines.push("END:VCALENDAR");
  const a=document.createElement("a");a.href=URL.createObjectURL(new Blob([lines.join("\r\n")],{type:"text/calendar"}));a.download="SD-CMS-Calendar.ics";a.click();
}
function emailCauseList(){
  const body=cases.map(c=>c.caseNumber+" - "+c.title+" ("+fmt(c.nextHearingEpochMs)+")").join("\n");
  location.href="mailto:?subject="+encodeURIComponent("SD-CMS Cause List")+"&body="+encodeURIComponent(body);
}
render();
</script>
</body>
</html>"""
    }
}
