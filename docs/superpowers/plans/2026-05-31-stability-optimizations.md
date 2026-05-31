# Stability Optimizations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the runtime stability risks found in the project review without broad UI or architecture changes.

**Architecture:** Keep the existing Java/Robolectric/Room structure. Add narrow asynchronous wrappers around recent-file reads, move MarkdownActivity initialization into a safe order, enforce file-size limits while reading, and discard stale search jobs with a generation token.

**Tech Stack:** Android Java, Room, Robolectric, JUnit 4, Markwon.

---

### Task 1: Activity Startup And Recent Files

**Files:**
- Modify: `app/src/main/java/com/example/markdownviewer/MarkdownActivity.java`
- Modify: `app/src/main/java/com/example/markdownviewer/MainActivity.java`
- Modify: `app/src/main/java/com/example/markdownviewer/RecentFilesManager.java`
- Test: `app/src/test/java/com/example/markdownviewer/MarkdownActivityTest.java`

- [ ] **Step 1: Write failing Activity startup test**

Create a Robolectric test that launches `MarkdownActivity` with a valid `content://` URI and asserts the Activity reaches a created state without throwing during early theme setup.

- [ ] **Step 2: Verify test fails**

Run: `.\gradlew.bat testDebugUnitTest --tests com.example.markdownviewer.MarkdownActivityTest`

- [ ] **Step 3: Implement minimal startup and async recent-file fix**

Initialize `viewModel` before `applyReaderTheme`, add async recent-file/scroll callbacks, and update callers to avoid main-thread Room reads.

- [ ] **Step 4: Verify Activity test passes**

Run: `.\gradlew.bat testDebugUnitTest --tests com.example.markdownviewer.MarkdownActivityTest`

### Task 2: File Size Guard

**Files:**
- Modify: `app/src/main/java/com/example/markdownviewer/MarkdownRepository.java`
- Test: `app/src/test/java/com/example/markdownviewer/MarkdownRepositoryTest.java`

- [ ] **Step 1: Write failing oversized-stream test**

Use Robolectric `ShadowContentResolver` with a `content://` URI whose metadata size is unknown and whose stream exceeds `Constants.MAX_FILE_SIZE`; expect `LoadResult.success` to be false.

- [ ] **Step 2: Verify test fails**

Run: `.\gradlew.bat testDebugUnitTest --tests com.example.markdownviewer.MarkdownRepositoryTest`

- [ ] **Step 3: Count bytes while reading**

Reject the file as soon as UTF-8 bytes read exceed `Constants.MAX_FILE_SIZE`.

- [ ] **Step 4: Verify repository test passes**

Run: `.\gradlew.bat testDebugUnitTest --tests com.example.markdownviewer.MarkdownRepositoryTest`

### Task 3: Search Race Guard

**Files:**
- Modify: `app/src/main/java/com/example/markdownviewer/SearchHelper.java`
- Test: `app/src/test/java/com/example/markdownviewer/SearchHelperTest.java`

- [ ] **Step 1: Write failing stale-result test where feasible**

Add coverage around clearing/destroying search state and generation behavior using real text views.

- [ ] **Step 2: Implement generation token**

Increment a search generation for each query and on destroy/clear, and apply results only when the generation still matches.

- [ ] **Step 3: Verify search tests pass**

Run: `.\gradlew.bat testDebugUnitTest --tests com.example.markdownviewer.SearchHelperTest`

### Task 4: Full Verification

**Files:**
- All touched source and tests.

- [ ] **Step 1: Run unit tests**

Run: `.\gradlew.bat testDebugUnitTest`

- [ ] **Step 2: Run lint**

Run: `.\gradlew.bat lintDebug`

- [ ] **Step 3: Run debug build**

Run: `.\gradlew.bat assembleDebug`
