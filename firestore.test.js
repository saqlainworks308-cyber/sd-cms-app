const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_counsel_1";
const BOB_UID = "bob_counsel_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTime() {
  return new Date();
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write cases", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/cases`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/cases`).doc("case_1").set({
      userId: ALICE_UID,
      caseNumber: "WP-101/2026",
      title: "Test Case",
      status: "active",
      priority: "high",
      nextHearingEpochMs: 1700000000000,
      filingDeadlineEpochMs: 0,
      createdAt: serverTime(),
      updatedAt: serverTime(),
    })
  );
});

test("Cross-user isolation: Bob cannot read or write Alice's cases or tasks", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection(`users/${ALICE_UID}/cases`).doc("case_a").set({
      userId: ALICE_UID,
      caseNumber: "CS-22/2026",
      title: "Dilawar v. State",
      status: "active",
      priority: "critical",
      nextHearingEpochMs: 1700000000000,
      filingDeadlineEpochMs: 1700050000000,
      createdAt: serverTime(),
      updatedAt: serverTime(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/cases`).doc("case_a").get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/cases`).get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/tasks`).doc("task_x").set({
      userId: BOB_UID,
      title: "Hijack",
      category: "filing",
      priority: "high",
      dueEpochMs: 1700000000000,
      completed: false,
      createdAt: serverTime(),
      updatedAt: serverTime(),
    })
  );
});

test("Shadow update & ghost field rejection on cases", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/cases`).doc("case_ghost").set({
      userId: ALICE_UID,
      caseNumber: "CR-90/2026",
      title: "Ghost Case",
      status: "active",
      priority: "standard",
      nextHearingEpochMs: 0,
      filingDeadlineEpochMs: 0,
      isAdminBypass: true,
      createdAt: serverTime(),
      updatedAt: serverTime(),
    })
  );
});

test("Owner can create, update, list, and delete valid case, task, integration, and event", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = serverTime();

  const caseRef = aliceDb.collection(`users/${ALICE_UID}/cases`).doc("case_valid_1");
  await assertSucceeds(
    caseRef.set({
      userId: ALICE_UID,
      caseNumber: "WP-404/2026",
      title: "Saqlain Enterprises v. Federation",
      courtName: "High Court",
      courtRoom: "Courtroom 4",
      judgeName: "Justice A. Khan",
      clientName: "Saqlain Enterprises",
      clientEmail: "client@example.com",
      opposingParty: "Federation",
      opposingCounsel: "State Counsel",
      stage: "Arguments",
      status: "active",
      priority: "critical",
      nextHearingEpochMs: 1794820000000,
      filingDeadlineEpochMs: 1794730000000,
      hearingDurationMinutes: 45,
      reminderMinutesBefore: 120,
      googleKeepUrl: "https://keep.google.com/#NOTE/abc123",
      googleKeepLabel: "Cross-exam points",
      googleSheetUrl: "https://docs.google.com/spreadsheets/d/12345/edit",
      calendarEventId: "evt_100",
      calendarSourceId: "src_1",
      notes: "Prepare precedent compilation.",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    caseRef.update({
      stage: "Final Arguments",
      updatedAt: serverTime(),
    })
  );

  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/cases`).get());

  const taskRef = aliceDb.collection(`users/${ALICE_UID}/tasks`).doc("task_valid_1");
  await assertSucceeds(
    taskRef.set({
      userId: ALICE_UID,
      caseId: "case_valid_1",
      caseNumber: "WP-404/2026",
      title: "File Written Synopsis",
      description: "Submit 3 copies before 11 AM",
      category: "filing",
      priority: "critical",
      dueEpochMs: 1794730000000,
      completed: false,
      reminderEnabled: true,
      keepNoteUrl: "https://keep.google.com/#NOTE/abc123",
      createdAt: now,
      updatedAt: now,
    })
  );

  const linkRef = aliceDb.collection(`users/${ALICE_UID}/integrations`).doc("link_ics_1");
  await assertSucceeds(
    linkRef.set({
      userId: ALICE_UID,
      type: "ics_calendar",
      label: "Chambers Google Calendar",
      url: "https://calendar.google.com/calendar/ical/example/basic.ics",
      autoSync: true,
      lastSyncedEpochMs: 1794730000000,
      syncedItemCount: 3,
      lastError: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  const evtRef = aliceDb.collection(`users/${ALICE_UID}/calendar_events`).doc("evt_1");
  await assertSucceeds(
    evtRef.set({
      userId: ALICE_UID,
      externalUid: "uid-999@google.com",
      sourceId: "link_ics_1",
      sourceLabel: "Chambers Google Calendar",
      title: "Hearing: WP-404/2026",
      location: "Courtroom 4",
      description: "Bench 2",
      startEpochMs: 1794820000000,
      endEpochMs: 1794823600000,
      allDay: false,
      linkedCaseId: "case_valid_1",
      keepNoteUrl: "https://keep.google.com/#NOTE/abc123",
      deviceEventId: "42",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(caseRef.delete());
});

test("Immutable createdAt cannot be altered on update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = serverTime();
  const ref = aliceDb.collection(`users/${ALICE_UID}/tasks`).doc("task_immut");
  await assertSucceeds(
    ref.set({
      userId: ALICE_UID,
      title: "Original",
      category: "general",
      priority: "standard",
      dueEpochMs: 1794730000000,
      completed: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    ref.update({
      createdAt: new Date(now.getTime() - 100000),
      updatedAt: serverTime(),
    })
  );
});
