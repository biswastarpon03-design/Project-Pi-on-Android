const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user cannot read or write responses", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("responses").get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("responses")
      .doc("resp_1")
      .set({
        userId: ALICE_UID,
        postId: "tg_42",
        postKey: "key_42",
        questionExcerpt: "What is pi?",
        answerText: "3.14159",
        platformSummary: "Telegram",
        createdAt: new Date(Date.now() - 1000),
        updatedAt: new Date(Date.now() - 1000),
      })
  );
});

test("Authenticated owner create, partial update, list, and delete mirror Kotlin repository", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const responsesCol = aliceDb.collection("users").doc(ALICE_UID).collection("responses");

  await assertSucceeds(
    responsesCol.doc("resp_1").set({
      userId: ALICE_UID,
      postId: "tg_projectpitelegram_42",
      postKey: "post_abc123",
      questionExcerpt: "Solve: 2x + 4 = 10?",
      answerText: "x = 3",
      platformSummary: "WhatsApp, Telegram",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(responsesCol.get());

  await assertSucceeds(
    responsesCol.doc("resp_1").update({
      answerText: "x = 3 (checked twice)",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(responsesCol.doc("resp_1").delete());
});

test("Rejects immutable field tampering, extra @DocumentId field, future timestamps, and oversized text", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const responsesCol = aliceDb.collection("users").doc(ALICE_UID).collection("responses");

  await assertSucceeds(
    responsesCol.doc("resp_1").set({
      userId: ALICE_UID,
      postId: "tg_42",
      postKey: "post_abc123",
      questionExcerpt: "What is pi?",
      answerText: "Approx 3.14",
      platformSummary: "Telegram",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(
    responsesCol.doc("resp_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    responsesCol.doc("resp_2").set({
      id: "resp_2",
      userId: ALICE_UID,
      postId: "tg_43",
      postKey: "post_43",
      questionExcerpt: "Q2?",
      answerText: "Answer",
      platformSummary: "Telegram",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(
    responsesCol.doc("resp_3").set({
      userId: ALICE_UID,
      postId: "tg_44",
      postKey: "post_44",
      questionExcerpt: "Q3?",
      answerText: "Answer",
      platformSummary: "Telegram",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  await assertFails(
    responsesCol.doc("resp_4").set({
      userId: ALICE_UID,
      postId: "tg_45",
      postKey: "post_45",
      questionExcerpt: "Q4?",
      answerText: "",
      platformSummary: "Telegram",
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Cross-user isolation prevents Bob from reading or modifying Alice's responses", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("responses")
      .doc("alice_resp")
      .set({
        userId: ALICE_UID,
        postId: "tg_42",
        postKey: "post_42",
        questionExcerpt: "Solve?",
        answerText: "42",
        platformSummary: "Telegram",
        createdAt: new Date(Date.now() - 10000),
        updatedAt: new Date(Date.now() - 10000),
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("responses").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("responses").doc("alice_resp").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("responses")
      .doc("alice_resp")
      .update({ answerText: "Hijacked", updatedAt: new Date(Date.now() - 1000) })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("responses").doc("alice_resp").delete()
  );
});
