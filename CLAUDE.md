Aquí tienes un prompt en inglés, detallado y listo para copiar en una herramienta de generación de aplicaciones con IA como Lovable, Bolt, Cursor o un generador de apps Android. Está pensado para crear una aplicación funcional, no solo un diseño visual, utilizando tu PDF como fuente de las preguntas, respuestas, explicaciones y temario.

Writing

# Build an Android Exam Preparation App Based on a PDF Study Guide

## 1\. Project Overview

Build a complete, modern, user-friendly Android application designed to help users prepare for an exam using the information contained in a provided PDF document.

The PDF contains:

- The syllabus, organized into different topics.
- Exam questions and their possible answers.
- The correct answer for each question.
- Explanations and theoretical content related to each topic.

The application must extract, organize, and structure this information into a searchable knowledge base that powers three distinct learning modes.

**Core requirement:** The PDF must be the primary source of truth. Do not invent questions, answers, explanations, or theoretical content. Preserve the original meaning and accuracy of all imported information.

The application must be fully functional, with persistent storage, intuitive navigation, progress tracking, and a polished native Android experience.

## 2\. Platform and Technology

Develop the application for Android smartphones, prioritizing performance, accessibility, and a responsive user interface.

Preferred technology stack:

- Kotlin.
- Jetpack Compose for the user interface.
- Material 3 design system.
- Room database for local persistent storage.
- DataStore for user preferences.
- Kotlin Coroutines and Flow for asynchronous operations and reactive data updates.
- Android Storage Access Framework for importing PDF files.

The application should work offline after the PDF has been imported and processed.

Use a clean, maintainable architecture, such as MVVM with clear separation between the UI, business logic, and data layers.

If a different framework is required by the development environment, use an appropriate Android-compatible alternative while preserving all the functional requirements.

## 3\. PDF Import and Content Processing

Create an initial setup screen that allows the user to import a PDF from their Android device.

After selecting the file, the application must:

1. Extract the text and content from the PDF.
2. Identify the syllabus topics and their hierarchy.
3. Identify questions, answer options, correct answers, and explanations.
4. Associate each question with the appropriate topic.
5. Extract and organize the theoretical content for each topic.
6. Detect duplicate questions and flag ambiguous or incomplete information.
7. Present an import summary showing the number of topics, questions, and explanations successfully processed.
8. Allow the user to review and correct parsing errors before confirming the import.
9. Store the processed content in the local database so it remains available after restarting the application.

Support text-based PDFs and, where feasible, scanned PDFs using OCR.

If any information cannot be reliably extracted, flag it for manual review instead of guessing.

Each question should have a unique identifier, its topic, question text, answer options, correct answer, explanation, and any relevant reference to the theoretical content.

Each topic should contain its title, theoretical material, subsections, and associated questions.

Allow the user to replace or re-import the PDF without unnecessarily duplicating existing content.

## 4\. Main Navigation and Dashboard

Create a clean home screen with three prominent learning modes:

1. **Study Topics**
2. **Live Questions**
3. **Exam Mode**

Also include:

- Overall learning progress.
- Total number of available questions.
- Number of topics completed or studied.
- Recent exam results.
- A shortcut to resume the last learning session.
- Access to exam history and settings.

Use a bottom navigation bar or another intuitive navigation pattern suitable for Android.

The interface should be available in Spanish by default, with a structure that allows future localization. Use clear labels, readable typography, accessible colors, and intuitive icons.

## 5\. Mode One: Study Topics

Create a dedicated section where users can browse the entire syllabus, organized by topic and subsection.

### Topic navigation

- Display all topics in a structured list.
- Allow users to expand and collapse topic sections.
- Show their reading progress.
- Allow users to search for specific topics or keywords.
- Allow users to jump directly to a selected topic.

### Reading experience

Each topic must open a dedicated reading screen displaying its complete theoretical content.

The content should be presented in a clean, readable format with:

- Headings and subheadings.
- Paragraphs and lists.
- Highlighted key concepts.
- Definitions and important terms.
- Tables, if they can be accurately extracted from the PDF.
- References to related questions where appropriate.

**Infinite scrolling requirement:** Implement continuous vertical scrolling for long topics, allowing the user to read the entire topic without being interrupted by pagination or artificial content boundaries. If a topic is very large, use efficient content rendering or incremental loading while preserving the continuous reading experience.

Additional features:

- Save the user's reading position.
- Resume reading where they left off.
- Mark topics as completed.
- Allow users to bookmark important sections.
- Provide a quick action to practice questions related to the current topic.
- Preserve the original meaning and structure of the source material.

The reading screen must remain responsive, even when dealing with very long topics.

## 6\. Mode Two: Live Questions

Create an interactive practice mode where users answer questions one at a time and receive immediate feedback after each answer.

### Session configuration

Before starting, let users choose between:

**A. Topic-based practice**

- Select one topic or multiple topics.
- Practice only questions associated with the selected topics.

**B. General random practice**

- Select questions randomly from the entire question bank.
- Allow questions from all topics to appear in the same session.

Additional configuration options:

- Number of questions, including all available questions.
- Option to prioritize unanswered or previously incorrect questions.
- Optional question shuffling.
- Display a progress indicator showing the current question and total questions.

### Question screen

For each question:

- Display the complete question text.
- Display all available answer options.
- Allow the user to select an answer.
- Prevent accidental multiple submissions for the same question.
- Provide a clear confirmation action if necessary.

Once the user submits an answer, immediately display:

1. Whether the selected answer is correct or incorrect.
2. The correct answer, clearly identified.
3. An explanation of why the answer is correct, using the explanation provided in the PDF.
4. The relevant theoretical content from the corresponding topic.
5. A button to read the complete relevant theory.
6. A button to continue to the next question.

Use clear visual feedback, such as green for correct answers and red for incorrect answers, while maintaining accessibility.

The correct answer must not be revealed before the user submits their response.

### End-of-session summary

At the end of a practice session, display:

- Total questions answered.
- Correct answers.
- Incorrect answers.
- Accuracy percentage.
- Performance breakdown by topic.
- Questions answered incorrectly.
- An option to repeat the incorrect questions.
- An option to start another session.

Store practice-session results locally so the user can track improvement over time.

## 7\. Mode Three: Exam Mode

Create a realistic exam simulator that reproduces the experience of taking a formal examination.

### Exam configuration

Allow the user to configure:

- The number of questions.
- Whether the exam covers the entire syllabus or selected topics.
- Random question selection.
- Random ordering of answer options, where appropriate.
- An optional time limit.
- The scoring rules, if specified in the source material or configured by the user.

If the original exam format is known, provide a preset that reproduces it. Otherwise, use sensible configurable defaults.

Make sure the exam cannot contain more questions than are available in the selected question pool.

### Exam experience

During the exam:

- Show one question at a time.
- Allow the user to select an answer.
- Provide Previous and Next navigation.
- Allow the user to change an answer before submitting the exam.
- Show the current question number and overall progress.
- Indicate which questions have been answered and which remain unanswered.
- If a timer is enabled, display the remaining time.
- Provide an exam overview screen before final submission.
- Ask for confirmation before submitting the completed exam.

**Important:** Do not reveal correct answers, explanations, or theoretical material while the exam is in progress.

When the user submits the exam, calculate and save the result before displaying the review screen.

### Final results screen

After the exam has been submitted, display:

- Final score.
- Number of correct answers.
- Number of incorrect answers.
- Number of unanswered questions.
- Percentage of correct answers.
- Pass or fail status, if a passing threshold has been configured.
- Performance by topic.
- Comparison with previous exams, when historical data is available.

Use a clear visual summary, including charts or progress indicators where appropriate.

### Exam review

Allow users to review every question after completing the exam.

For each question, show:

- The original question.
- All answer options.
- The answer selected by the user.
- The correct answer.
- Whether the response was correct or incorrect.
- The official explanation from the PDF.
- The relevant theoretical content, including a shortcut to the corresponding section of the syllabus.

Provide filters to review:

- All questions.
- Incorrect answers.
- Correct answers.
- Unanswered questions.

Allow users to revisit the relevant theory directly from the review screen without losing their place in the exam review.

## 8\. Exam History and Performance Tracking

Create a persistent exam history section that stores every completed exam.

The history must remain available after the application is closed or the device is restarted.

For each exam, store:

- Unique exam identifier.
- Date and time.
- Number of questions.
- Final score and accuracy percentage.
- Correct, incorrect, and unanswered counts.
- Topics included.
- Time spent, if timing is enabled.
- Detailed answers and the correct answers.
- The exam configuration used.

### History interface

Display completed exams in reverse chronological order, with the most recent first.

Each entry should show the date, score, accuracy, and number of questions.

When a user selects an exam, allow them to:

- Open the detailed results.
- Review all questions and explanations.
- Identify recurring mistakes.
- Compare performance across different exams.
- Repeat the exam with a newly randomized selection of questions.

### Progress analytics

Include a statistics dashboard showing:

- Average exam score.
- Best exam score.
- Recent performance trend.
- Total exams completed.
- Total questions answered.
- Overall accuracy.
- Accuracy by topic.
- Topics where the user performs well.
- Topics that need more study.
- Frequently missed questions.

Calculate statistics using actual stored results. Do not fabricate historical data.

## 9\. Question Bank and Learning Logic

Create a centralized question repository shared by all three learning modes.

Every question must be linked to its original topic and theoretical explanation.

Implement logic to:

- Randomize questions without accidentally duplicating them within a session, unless explicitly requested.
- Preserve the original correct answer when shuffling answer options.
- Track the user's answer history for each question.
- Identify questions frequently answered incorrectly.
- Support practice of previously incorrect questions.
- Maintain consistent scoring across sessions.
- Avoid changing the source content silently.
- Handle incomplete or invalid questions safely.

If the PDF does not provide an explicit scoring rule, make the scoring configurable instead of assuming that incorrect answers incur a penalty.

Keep the correct answers and explanations hidden during exam sessions until submission.

## 10\. Local Database and Data Integrity

Use a persistent local database with appropriate entities and relationships.

Suggested entities:

- Topic
- TheorySection
- Question
- AnswerOption
- PracticeSession
- PracticeResponse
- ExamAttempt
- ExamResponse
- Bookmark
- UserSettings
- ImportedDocument

Use unique identifiers and database migrations where necessary.

Ensure that:

- Completed exam records are not lost when the app restarts.
- Partial exam sessions can optionally be resumed.
- Exam answers are stored accurately.
- Re-importing a PDF does not corrupt previous exam history.
- Deleting or replacing study material does not silently delete historical exam results.
- Data operations are safe and consistent.

The initial version should not require an account, a subscription, or a cloud backend. All essential functionality should work locally.

## 11\. UI/UX and Visual Design

Create a polished, modern Android application with a professional educational look and feel.

Design principles:

- Material 3 components.
- Clean layouts and clear visual hierarchy.
- Light and dark themes.
- Accessible color contrast.
- Comfortable reading typography.
- Consistent spacing and rounded cards.
- Subtle animations and transitions.
- Clear empty states, loading indicators, and error messages.
- Responsive layouts for different Android screen sizes.
- Accessible touch targets and support for Android accessibility features.

Avoid clutter, excessive animations, and unnecessary gamification.

Use progress indicators and encouraging feedback to make studying motivating without distracting from the learning process.

## 12\. Settings and Additional Features

Provide a settings screen with:

- Light, dark, or system theme.
- Preferred language.
- Default number of questions per practice session.
- Default exam length.
- Optional exam timer.
- Passing score threshold.
- PDF import and replacement.
- Export and backup of exam history, where feasible.
- Reset progress with an explicit confirmation step.

Where feasible, allow exporting exam history as CSV or JSON.

Keep all settings persistent.

## 13\. Error Handling and Edge Cases

Handle the following scenarios:

- The selected PDF is corrupted or unreadable.
- The PDF contains scanned pages.
- The PDF has an unexpected structure.
- Questions have missing answer options.
- Correct answers cannot be identified confidently.
- Some topics have no associated questions.
- A user starts a session with too few available questions.
- The application is closed during a practice session or exam.
- The user attempts to submit an incomplete exam.
- The PDF is replaced after exams have already been completed.

Display understandable error messages and provide recovery options.

Never silently assign a correct answer when the source is ambiguous.

## 14\. Testing and Quality Assurance

Implement automated tests for critical functionality.

Include tests for:

- PDF parsing and topic identification.
- Question-to-topic associations.
- Correct-answer validation.
- Question randomization.
- Answer-option shuffling.
- Score calculations.
- Exam submission.
- Exam history persistence.
- Topic-specific performance statistics.
- Database migrations.
- Recovery from incomplete sessions.

Verify that the app builds successfully and that the three learning modes work end to end.

Use sample test data only for development and testing. Clearly separate test data from the user's imported study material.

## 15\. Development Deliverables

Deliver a complete Android project that includes:

- All application source code.
- Gradle configuration and dependencies.
- Database schema and migrations.
- PDF import and processing implementation.
- All three fully functional learning modes.
- Exam history and analytics.
- Automated tests.
- Instructions to build and install the app.
- A debug APK or installable Android package, if supported by the development environment.

Do not deliver only a clickable prototype, mockup, or static UI.

All buttons, navigation elements, filters, answer selections, database operations, and exam calculations must work.

Do not leave core features as placeholders or simulate functionality with hardcoded results.

## 16\. Implementation Priorities

Build the application in the following order:

**Phase 1:** Project setup, database architecture, PDF import, content extraction, and validation.

**Phase 2:** Topic browser, theory reader, continuous scrolling, and reading progress.

**Phase 3:** Live Questions mode, immediate feedback, and theory explanations.

**Phase 4:** Exam Mode, random exam generation, scoring, and detailed review.

**Phase 5:** Persistent exam history, statistics, and performance analytics.

**Phase 6:** Settings, accessibility, error handling, automated tests, and final polish.

Prioritize data accuracy, reliable exam calculations, and persistent storage over decorative features.

## Final Acceptance Criteria

The application is considered complete only when a user can:

1. Import their own PDF and verify the extracted syllabus and questions.
2. Read every topic from beginning to end using continuous scrolling.
3. Practice questions from a selected topic or from the entire question bank.
4. Receive immediate feedback, correct answers, explanations, and relevant theory in Live Questions mode.
5. Take a randomized exam without seeing the answers before submission.
6. View the final exam score and review every question with its explanation and relevant theory.
7. Close and reopen the app without losing completed exam history.
8. Track performance over time and identify weak topics.
9. Use all core features offline after importing the PDF.

**Start by inspecting the PDF structure and designing the data model. Then implement the application incrementally, validating each phase before proceeding to the next. Make reasonable implementation decisions autonomously, but never invent source material or compromise the correctness of the exam content.**

