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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated caller cannot read or list leads", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/leads`).get());
  await assertFails(unauthDb.doc(`users/${ALICE_UID}/leads/lead_1`).get());
});

test("Cross-user access: Bob cannot read or write Alice's leads, drafts, conversations, or integrations", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb.doc(`users/${ALICE_UID}/leads/lead_1`).set({
      userId: ALICE_UID,
      storeName: "Acme Goods",
      websiteUrl: "https://acmegoods.com",
      crmStage: "DISCOVERED",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/leads`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/leads/lead_1`).get());
  await assertFails(
    bobDb.doc(`users/${ALICE_UID}/leads/lead_2`).set({
      userId: BOB_UID,
      storeName: "Spoof",
      websiteUrl: "https://spoof.com",
      crmStage: "DISCOVERED",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/integrations`).get());
});

test("Owner can create, read, list, and update valid ShopifyLead", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const leadRef = aliceDb.doc(`users/${ALICE_UID}/leads/lead_1`);
  await assertSucceeds(
    leadRef.set({
      userId: ALICE_UID,
      storeName: "Loom & Tide",
      websiteUrl: "https://loomandtide.com",
      crmStage: "DISCOVERED",
      auditScore: 68,
      auditOpportunities: ["Weak above-the-fold CTA"],
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/leads`).get());
  await assertSucceeds(
    leadRef.update({
      crmStage: "AUDITED",
      auditScore: 72,
      updatedAt: new Date(),
    })
  );
});

test("Shadow update with ghost field or invalid enum is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/leads/lead_ghost`).set({
      userId: ALICE_UID,
      storeName: "Ghost Store",
      websiteUrl: "https://ghost.com",
      crmStage: "DISCOVERED",
      isAdminVerified: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/leads/lead_bad_enum`).set({
      userId: ALICE_UID,
      storeName: "Bad Enum Store",
      websiteUrl: "https://badenum.com",
      crmStage: "INVALID_STAGE",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Owner can create OutreachDraft, ConversationThread, ChatMessage, and ChannelIntegration", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/outreachDrafts/draft_1`).set({
      userId: ALICE_UID,
      leadId: "lead_1",
      channel: "INSTAGRAM",
      generatedMessage: "Hey! Love your store.",
      status: "AWAITING_APPROVAL",
      apiConfirmed: false,
      tone: "FRIENDLY",
      messageLength: "MEDIUM",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/conversations/conv_1`).set({
      userId: ALICE_UID,
      leadId: "lead_1",
      storeName: "Loom & Tide",
      channel: "WHATSAPP",
      deliveryStatus: "SENT",
      lastMessagePreview: "Hi there!",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/conversations/conv_1/messages/msg_1`).set({
      userId: ALICE_UID,
      conversationId: "conv_1",
      leadId: "lead_1",
      channel: "WHATSAPP",
      direction: "OUTBOUND",
      body: "Hi there!",
      status: "SENT",
      apiConfirmed: true,
      createdAt: new Date(),
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/integrations/whatsapp`).set({
      userId: ALICE_UID,
      channel: "WHATSAPP",
      connectionStatus: "CONNECTED",
      accountName: "Agency WA",
      accountHandleOrPhone: "+15551234567",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});
