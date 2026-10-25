# ==============================================================================
#                      PROJECT N.E.X. / F.R.I.D.A.Y.
#                 MASTER ARCHITECTURE & SYSTEM BLUEPRINT
# ==============================================================================
# Document Version: 2.0 (Live Internet Web Search & Autonomous Agent Edition)
# Architecture: Autonomous Voice-First Mobile AI Agent with RAG Web Browsing
# Target OS: Android 8.0 (API 26) to Android 14 (API 34)
# Tech Stack: Kotlin, Jetpack Compose, Kotlin Coroutines, Gradle 8.5, Java 17
# AI Mainframe: Groq LLaMA 3.3 70B Versatile (Native HttpURLConnection Engine)
# Live Web Search Engine: Tavily AI Search API (Perplexity/ChatGPT class live RAG)
# Core Persona: Tony Stark's J.A.R.V.I.S. & F.R.I.D.A.Y. (British Cadence, 'Sir')
# ==============================================================================

## 1. PROJECT OVERVIEW & CAPABILITIES
Nex is an autonomous, hands-free, voice-first Android AI companion.
- Zero Chat UI Needed: Driven by an Iron Man Arc Reactor HUD interface.
- Real-Time Global Internet Browsing: Integrated with Tavily AI Search to scan live 
  web pages, news, stock rates, cricket scores, and weather worldwide in real-time.
- 24/7 Background Sentinel: Functions with screen locked or app minimized.
- Autonomous Tool Execution: 70B LLaMA model extracts natural user intent in any 
  language or slang and executes real device actions using execution tags ([EXEC:...]).
- God-Mode Device Control: Powered by Android Accessibility Service to navigate,
  auto-scroll reels, take screenshots, lock the device, and launch apps.

---

## 2. COMPLETE FILE TREE

```text
NEX/
├── .github/
│   └── workflows/
│       └── build.yml               # Automated CI/CD compilation engine for GitHub Actions
├── app/
│   ├── build.gradle.kts            # App-level dependencies, Compose BOM, SDK 34 config
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml # Permissions, services, queries, and accessibility declaration
│           ├── java/com/nex/ai/
│           │   ├── AiEngine.kt                   # Groq LLaMA 3.3 70B + Tavily Live Web RAG Engine
│           │   ├── HardwareManager.kt            # Offline fast-path, BAL bypass & AI action dispatcher
│           │   ├── MainActivity.kt               # Iron Man Arc Reactor HUD (Compose) & Power management
│           │   ├── NexAccessibilityService.kt    # God-Mode screen controller (Screenshot, Lock, Scroll)
│           │   ├── NexCoreService.kt             # 24/7 Background Sentinel, CPU WakeLock, screen auto-wake
│           │   ├── NotificationSentinelService.kt# Private WhatsApp/SMS notification listener
│           │   └── VoiceManager.kt               # STT & TTS engine with background AudioAttributes
│           └── res/
│               ├── values/
│               │   └── strings.xml               # App label strings
│               └── xml/
│                   └── accessibility_service_config.xml # Accessibility permissions and gesture configuration
├── build.gradle.kts                # Top-level build plugins (AGP 8.3.0, Kotlin 1.9.22)
├── gradle.properties               # JVM memory optimization (2048m) & AndroidX flags
├── settings.gradle.kts             # Module roots & repository resolution
└── NEX_SYSTEM_BLUEPRINT.md         # Master System State & Architectural Blueprint
