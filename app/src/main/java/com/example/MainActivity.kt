package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.SecureTokenStore
import com.example.core.configureAppCheck
import com.example.data.ai.FirebaseAiOutreachService
import com.example.data.model.AiAssistantMode
import com.example.data.model.OutreachChannel
import com.example.data.model.OutreachDraft
import com.example.data.remote.MetaGraphApiClient
import com.example.data.remote.ShopifyStoreScanner
import com.example.data.repository.FirestoreOutreachRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.components.ActionFeedbackBanner
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.ChannelsScreen
import com.example.ui.screens.ConversationDetailScreen
import com.example.ui.screens.DiscoveryScreen
import com.example.ui.screens.InboxScreen
import com.example.ui.screens.LeadDetailScreen
import com.example.ui.screens.OutreachScreen
import com.example.ui.theme.LeadFinderTheme
import com.example.ui.viewmodel.ActionUiState
import com.example.ui.viewmodel.LeadFinderViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_DISCOVERY = "discovery"
const val ROUTE_OUTREACH = "outreach"
const val ROUTE_INBOX = "inbox"
const val ROUTE_ANALYTICS = "analytics"
const val ROUTE_CHANNELS = "channels"
const val ROUTE_LEAD_DETAIL = "lead_detail/{leadId}"
const val ROUTE_CONVERSATION_DETAIL = "conversation/{conversationId}"

private data class TopTabItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val TopTabs = listOf(
    TopTabItem(ROUTE_DISCOVERY, R.string.nav_discover, Icons.Rounded.TravelExplore, Icons.Outlined.TravelExplore),
    TopTabItem(ROUTE_OUTREACH, R.string.nav_outreach, Icons.AutoMirrored.Rounded.Send, Icons.AutoMirrored.Outlined.Send),
    TopTabItem(ROUTE_INBOX, R.string.nav_inbox, Icons.Rounded.Forum, Icons.Outlined.Forum),
    TopTabItem(ROUTE_ANALYTICS, R.string.nav_analytics, Icons.Rounded.Analytics, Icons.Outlined.Analytics),
    TopTabItem(ROUTE_CHANNELS, R.string.nav_channels, Icons.Rounded.Hub, Icons.Outlined.Hub)
)

class MainActivity : ComponentActivity() {
    private var metaOauthTokenState by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // App Check MUST run before setContent, ViewModels, or Firebase operations
        configureAppCheck(this, intent)
        extractOAuthFragmentToken(intent)

        enableEdgeToEdge()
        setContent {
            LeadFinderTheme {
                AppNavigation(oauthReceivedToken = metaOauthTokenState)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractOAuthFragmentToken(intent)
    }

    private fun extractOAuthFragmentToken(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "leadfinder" && data.host == "oauth") {
            val fragment = data.fragment.orEmpty()
            val token = fragment.split("&")
                .firstOrNull { it.startsWith("access_token=") }
                ?.substringAfter("access_token=")
                .orEmpty()
            if (token.isNotBlank()) {
                metaOauthTokenState = token
            }
        }
    }
}

@Composable
fun AppNavigation(oauthReceivedToken: String) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        key(user.uid) {
            LeadFinderAppShell(
                currentUserId = user.uid,
                userDisplayName = user.displayName.orEmpty(),
                userEmail = user.email.orEmpty(),
                oauthReceivedToken = oauthReceivedToken
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeadFinderAppShell(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String,
    oauthReceivedToken: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: LeadFinderViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                LeadFinderViewModel(
                    repository = FirestoreOutreachRepository(db),
                    scanner = ShopifyStoreScanner(),
                    metaClient = MetaGraphApiClient(),
                    aiService = FirebaseAiOutreachService(),
                    tokenStore = SecureTokenStore(app),
                    currentUserId = currentUserId,
                    userDisplayName = userDisplayName,
                    userEmail = userEmail
                )
            }
        }
    )

    val dashboard by viewModel.dashboardState.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopTabRoute = TopTabs.any { it.route == currentRoute }

    var pendingEditorDraft by remember { mutableStateOf<OutreachDraft?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            topBar = {
                if (isTopTabRoute) {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    )
                }
            },
            bottomBar = {
                if (isTopTabRoute && !useRail) {
                    NavigationBar {
                        TopTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopTabRoute && useRail) {
                    NavigationRail {
                        TopTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) }
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    ActionFeedbackBanner(
                        state = actionState,
                        onDismiss = { viewModel.clearActionState() },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )

                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_DISCOVERY,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(ROUTE_DISCOVERY) {
                            DiscoveryScreen(
                                leads = dashboard.leads,
                                isBusy = actionState is ActionUiState.Busy,
                                onScanStore = { url, niche, location, ig, wa, consent ->
                                    viewModel.scanAndAuditStore(url, niche, location, ig, wa, consent)
                                },
                                onOpenLead = { leadId ->
                                    navController.navigate("lead_detail/$leadId")
                                }
                            )
                        }

                        composable(ROUTE_OUTREACH) {
                            OutreachScreen(
                                leads = dashboard.leads,
                                drafts = dashboard.drafts,
                                instagramIntegration = dashboard.instagramIntegration,
                                whatsappIntegration = dashboard.whatsappIntegration,
                                editingDraftInitial = pendingEditorDraft,
                                onClearInitialEditor = { pendingEditorDraft = null },
                                onSaveDraft = { viewModel.saveCustomizedDraft(it) },
                                onRegenerateDraft = { viewModel.regenerateDraftWithAi(it) },
                                onApproveDraft = { lead, draft -> viewModel.approveDraft(lead, draft) },
                                onSendInstagramApi = { lead, draft -> viewModel.sendInstagramApiMessage(lead, draft) },
                                onSendWhatsAppApi = { lead, draft, useTpl -> viewModel.sendWhatsAppApiMessage(lead, draft, useTpl) },
                                onRecordManualOutreach = { lead, ch, notes -> viewModel.recordManualOutreach(lead, ch, notes) },
                                onOpenConversationForLead = { leadId, ch ->
                                    val conv = dashboard.conversations.firstOrNull {
                                        it.leadId == leadId && it.channel == ch.name
                                    }
                                    if (conv != null) {
                                        viewModel.setActiveConversation(conv.id)
                                        navController.navigate("conversation/${conv.id}")
                                    } else {
                                        navController.navigate(ROUTE_INBOX)
                                    }
                                }
                            )
                        }

                        composable(ROUTE_INBOX) {
                            InboxScreen(
                                leads = dashboard.leads,
                                drafts = dashboard.drafts,
                                conversations = dashboard.conversations,
                                instagramIntegration = dashboard.instagramIntegration,
                                whatsappIntegration = dashboard.whatsappIntegration,
                                onOpenConversation = { convId ->
                                    viewModel.setActiveConversation(convId)
                                    navController.navigate("conversation/$convId")
                                },
                                onSyncInstagramConversations = { viewModel.syncInstagramConversations() },
                                onProcessWebhookPayload = { json -> viewModel.processIncomingWebhookJson(json) }
                            )
                        }

                        composable(ROUTE_ANALYTICS) {
                            AnalyticsScreen(
                                analytics = dashboard.analytics,
                                leads = dashboard.leads,
                                onOpenLead = { leadId ->
                                    navController.navigate("lead_detail/$leadId")
                                }
                            )
                        }

                        composable(ROUTE_CHANNELS) {
                            ChannelsScreen(
                                instagramIntegration = dashboard.instagramIntegration,
                                whatsappIntegration = dashboard.whatsappIntegration,
                                aiMode = dashboard.userProfile?.aiAssistantMode ?: AiAssistantMode.MANUAL_APPROVAL.name,
                                userEmail = userEmail,
                                oauthReceivedToken = oauthReceivedToken,
                                onConnectInstagram = { token, appId -> viewModel.connectInstagramAccount(token, appId) },
                                onSyncInstagram = { viewModel.syncInstagramConversations() },
                                onConnectWhatsApp = { token, phoneId, wabaId, verify ->
                                    viewModel.connectWhatsAppBusiness(token, phoneId, wabaId, verify)
                                },
                                onDisconnectChannel = { viewModel.disconnectChannel(it) },
                                onUpdateAiMode = { viewModel.updateAiAssistantMode(it) },
                                onSignOut = { signOutUser(credentialManager, scope) }
                            )
                        }

                        composable(
                            route = ROUTE_LEAD_DETAIL,
                            arguments = listOf(navArgument("leadId") { type = NavType.StringType })
                        ) { entry ->
                            val leadId = entry.arguments?.getString("leadId").orEmpty()
                            val lead = dashboard.leads.firstOrNull { it.id == leadId }
                            LeadDetailScreen(
                                lead = lead,
                                drafts = dashboard.drafts,
                                onBack = { navController.popBackStack() },
                                onUpdateCrmStage = { id, stage -> viewModel.updateLeadCrmStage(id, stage) },
                                onUpdateWhatsAppCompliance = { id, consent, optOut ->
                                    viewModel.updateWhatsAppCompliance(id, consent, optOut)
                                },
                                onOpenOutreachEditor = { draft ->
                                    pendingEditorDraft = draft
                                    navController.navigate(ROUTE_OUTREACH)
                                },
                                onLogManualOutreach = { targetLead, ch, notes ->
                                    viewModel.recordManualOutreach(targetLead, ch, notes)
                                }
                            )
                        }

                        composable(
                            route = ROUTE_CONVERSATION_DETAIL,
                            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
                        ) { entry ->
                            val convId = entry.arguments?.getString("conversationId").orEmpty()
                            val thread = dashboard.conversations.firstOrNull { it.id == convId }
                            ConversationDetailScreen(
                                thread = thread,
                                messages = activeMessages,
                                aiMode = dashboard.userProfile?.aiAssistantMode ?: AiAssistantMode.MANUAL_APPROVAL.name,
                                onBack = {
                                    viewModel.setActiveConversation(null)
                                    navController.popBackStack()
                                },
                                onGenerateAiReply = { viewModel.generateAiReplyForThread(it) },
                                onSendReply = { targetThread, text, aiGen ->
                                    viewModel.sendThreadReply(targetThread, text, aiGen)
                                },
                                onToggleHumanHandoff = { viewModel.toggleHumanHandoff(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}
