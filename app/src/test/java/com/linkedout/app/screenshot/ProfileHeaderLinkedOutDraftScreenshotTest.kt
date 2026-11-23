package com.linkedout.app.screenshot

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linkedout.app.R
import com.linkedout.app.core.model.Post
import com.linkedout.app.core.model.PostStats
import com.linkedout.app.di.appModule
import com.linkedout.app.navigation.LocalReadableInset
import com.linkedout.app.ui.component.Avatar
import com.linkedout.app.ui.component.BannerAction
import com.linkedout.app.ui.component.BoldButton
import com.linkedout.app.ui.component.GlassTarget
import com.linkedout.app.ui.component.PostCard
import com.linkedout.app.ui.component.Zone
import com.linkedout.app.ui.component.ZoneGap
import com.linkedout.app.ui.component.statusBarTop
import com.linkedout.app.ui.glass.LocalGlass
import com.linkedout.app.ui.glass.glassZone
import com.linkedout.app.ui.icon.LinkedOutIcons
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 37), nothing of app/src/main changes: LinkedOut's profile takes the header
 * MTGA built and the user validated (MTGA points 28 and 21), with LinkedIn's fields. The banner 150 dp edge to edge
 * from the window's top (status bar shadow kept; its picture is a STAND-IN, a vertical Brush of primary to tertiary),
 * no Back button (the system gesture) and no Refresh button (a pull does it), the avatar 90 dp in a 2 dp ring of the
 * page's colour at the start over the banner's bottom edge, Follow at the end below the banner (Material 3 Expressive's
 * medium button, 56 dp, round, drawn as a glass zone: filled with the primary and its label in onPrimary, or once
 * followed the same size, glass only, with a check), then at the start the name (titleLarge bold, two lines at most,
 * a company too), the headline (a person's "headline", a company's industry), the followers on one line with a bold
 * number, the about text as the bio (four lines at most in the draft), then location, current company and school on one
 * wrapping line with their icons, and a company's website on its own row. No joined, no verified mark, no posts or
 * following counts, no connections ("500+" is not read by the app).
 * Frames: profile_header_linkedout_draft_person, _company, _following, _long, _pull (the pull of LinkedOut's own mark,
 * 72 dp with no disc, over the banner, the card unchanged under it), _today (ProfileZone's redraw of today's card, for
 * the comparison), each on phone and tablet, light and dark, and the edge pictures (200 % text, 360 dp phone) of the
 * person and the long one, and of the company.
 * Ports:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/ProfileHeaderDraftScreenshotTest.kt#ProfileHeaderDraftScreenshotTest
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/feed/ProfileHeader.kt#ProfileHeader
 * Icons, Material Symbols Rounded: person_add, location_on, domain (the symbol Android names "business" in its
 * Material Icons), school, link
 * https://github.com/google/material-design-icons/blob/master/symbols/android/person_add/materialsymbolsrounded/person_add_24px.xml
 * https://github.com/google/material-design-icons/blob/master/symbols/android/location_on/materialsymbolsrounded/location_on_24px.xml
 * https://github.com/google/material-design-icons/blob/master/symbols/android/domain/materialsymbolsrounded/domain_24px.xml
 * https://github.com/google/material-design-icons/blob/master/symbols/android/school/materialsymbolsrounded/school_24px.xml
 * https://github.com/google/material-design-icons/blob/master/symbols/android/link/materialsymbolsrounded/link_24px.xml
 * The pull layer and the mark stand-in are the local draft's (LoadingMark does not draw on Robolectric), the today card
 * its redraw of ProfileZone:
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/test/java/com/linkedout/app/screenshot/PullLoadDraftScreenshotTest.kt#ProposedIndicator
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/test/java/com/linkedout/app/screenshot/PullLoadDraftScreenshotTest.kt#MarkStandIn
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/feed/FeedScreen.kt#ProfileZone
 * not ported: joined (the calendar_month icon and its row), following and posts counts of ProfileHeaderMetrics, the
 * verified mark (VerifiedIcon and the draft's blue disc), the X avatar suffix rewrite (originalAvatar and its
 * AvatarVariant) and the X banner size rewrite (bannerFull and its BannerWithSize): LinkedIn has none of them;
 * compactCount (the counts are fixed strings in the draft); the real ProfileHeader's AsyncImage, media policy hold,
 * onOpenAvatar and onOpenBanner and LocalUriHandler (no network picture on Robolectric, the draft's clicks do nothing),
 * its websiteMax and BoxWithConstraints (the website has its own row here), the X page shell script
 * names listed at its end: _cr, _createMdxContent, _missingMdxReference, adopt, assistantScriptFn, broken, button,
 * disableAnimation, done, fits, MDXContent, onLoad, owned, pick, report, schedule, settle, smooth, stop, touchesChrome,
 * type (a cached web page's scripts, not code); the draft's Screen content parameter and Post list of X posts (LinkedIn
 * posts instead); the pull's snapshotFlow, haptic tick and animated hold (a picture feels nothing, Robolectric holds the
 * clock still).
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class ProfileHeaderLinkedOutDraftScreenshotTest : ScreenshotBase() {

    // PostCard reads the post marks through Koin.
    @Before
    fun startKoinOnce() {
        startKoin {
            androidContext(compose.activity.applicationContext)
            modules(appModule)
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    // The cards write the age against the clock, so the data is dated from it, as the other drafts do.
    private val now = System.currentTimeMillis()

    private fun posts(content: HeaderContent) = listOf(
        Post(
            id = "7392000000000000011",
            authorHandle = content.handle,
            authorName = content.name,
            text = "Merci aux équipes qui ont fait de cette semaine une réussite, sur chaque site et à chaque poste.",
            publishedAtMillis = now - 3 * 60 * 60 * 1000,
            permalink = "https://www.linkedin.com/posts/${content.handle}_activity-7392000000000000011-abcd",
            stats = PostStats(replies = 12, reposts = 31, likes = 540)
        ),
        Post(
            id = "7392000000000000012",
            authorHandle = content.handle,
            authorName = content.name,
            text = "Trois enseignements de notre démarche qualité, partagés avec celles et ceux qui la portent.",
            publishedAtMillis = now - 2 * 24 * 60 * 60 * 1000,
            permalink = "https://www.linkedin.com/posts/${content.handle}_activity-7392000000000000012-efgh",
            stats = PostStats(replies = 4, reposts = 9, likes = 210)
        )
    )

    // A person at rest: the headline, three lines of about, location, company and school.
    @Test
    fun person() = capture("profile_header_linkedout_draft_person") { Screen(following = false, content = PersonContent) }

    // A company: its industry under the name, a website on its own row, four lines of about.
    @Test
    fun company() = capture("profile_header_linkedout_draft_company") { Screen(following = false, content = CompanyContent) }

    // The second state of the button: Following.
    @Test
    fun followingState() = capture("profile_header_linkedout_draft_following") { Screen(following = true, content = PersonContent) }

    // Long content: a two line name, a long headline and location, an about cut at four lines.
    @Test
    fun long() = capture("profile_header_linkedout_draft_long") { Screen(following = false, content = LongContent) }

    // The profile pulled down: LinkedOut's own mark, 72 dp with no disc, over the banner, the card unchanged under it.
    @Test
    fun pull() = capture("profile_header_linkedout_draft_pull") {
        Box(Modifier.fillMaxSize()) {
            Screen(following = false, content = PersonContent)
            PullIndicator(fraction = 1f, refreshing = false, modifier = Modifier.align(Alignment.TopCenter).padding(top = statusBarTop()))
        }
    }

    // Today's card, ProfileZone, for the comparison.
    @Test
    fun today() = capture("profile_header_linkedout_draft_today") { Today(content = PersonContent) }

    // Edge: 200 % text and a 360 dp phone.
    @Test
    fun edge() = captureEdge("profile_header_linkedout_draft_person") { Screen(following = false, content = PersonContent) }

    @Test
    fun companyEdge() = captureEdge("profile_header_linkedout_draft_company") { Screen(following = false, content = CompanyContent) }

    // The same long content at 200 % text and a 360 dp phone.
    @Test
    fun longEdge() = captureEdge("profile_header_linkedout_draft_long") { Screen(following = false, content = LongContent) }

    @Composable
    private fun Screen(following: Boolean, content: HeaderContent) {
        Column(Modifier.fillMaxSize()) {
            DraftHeader(following, content)
            Column(
                Modifier.padding(horizontal = LocalReadableInset.current),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                posts(content).forEach { PostCard(post = it, onClick = {}, onOpenLink = {}, onDownload = {}) }
            }
        }
    }

    // Today: the card under the status bar, then the posts.
    @Composable
    private fun Today(content: HeaderContent) {
        Column(Modifier.fillMaxSize().padding(top = statusBarTop())) {
            ProfileCard(content)
            Column(
                Modifier.padding(horizontal = LocalReadableInset.current),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                posts(content).forEach { PostCard(post = it, onClick = {}, onOpenLink = {}, onDownload = {}) }
            }
        }
    }

    /**
     * FeedScreen.kt's ProfileZone with fixed data, as PullLoadDraftScreenshotTest redraws it: Back top left, Refresh top
     * right, the avatar, the name, the handle and Follow, centred, inset 12 dp in the card.
     * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/feed/FeedScreen.kt#ProfileZone
     */
    @Composable
    private fun ProfileCard(content: HeaderContent) {
        Zone(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = ZoneGap)
        ) {
            Box {
                Box(Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 12.dp)) {
                    BannerAction(icon = LinkedOutIcons.ArrowBack, label = "Back", onClick = {})
                }
                Box(Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = 12.dp)) {
                    BannerAction(icon = LinkedOutIcons.Refresh, label = "Refresh", onClick = {})
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Avatar(url = null, name = content.name, size = 88.dp)
                    Text(content.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text(
                        "@${content.handle}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    BoldButton(onClick = {}, filled = true, modifier = Modifier.padding(top = 8.dp)) { Text("Follow") }
                }
            }
        }
    }

    // The proposal's pull layer as PullLoadDraftScreenshotTest draws it: no Surface, no glass, no shadow; the mark alone
    // at 72 dp sliding down from the top edge by fraction x threshold.
    // https://github.com/213YaZ786/LinkedOut/blob/main/app/src/test/java/com/linkedout/app/screenshot/PullLoadDraftScreenshotTest.kt#ProposedIndicator
    @Composable
    private fun PullIndicator(fraction: Float, refreshing: Boolean, modifier: Modifier) {
        val threshold = PullToRefreshDefaults.PositionalThreshold
        Box(
            modifier.graphicsLayer {
                translationY = fraction * threshold.toPx() - this.size.height
                alpha = maxOf((fraction * 2f).coerceIn(0f, 1f), if (refreshing) 1f else 0f)
            }
        ) {
            MarkStandIn(72.dp)
        }
    }

    // LoadingMark's two bitmaps as plain images, at any size: the running mark's place in a picture.
    @Composable
    private fun MarkStandIn(size: Dp) {
        val body = ImageBitmap.imageResource(R.drawable.lo_mark_body)
        val shine = ImageBitmap.imageResource(R.drawable.lo_mark_shine)
        Box(Modifier.size(size)) {
            Image(body, contentDescription = null, modifier = Modifier.fillMaxSize())
            Image(shine, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}

// The fields of LinkedIn's Feed (core/model Post.kt): displayName, avatarUrl, bannerUrl, bio, location, currentCompany,
// school, headline (a company's industry), stats.followers; the website of a company.
private class HeaderContent(
    val name: String, val handle: String, val headline: String?, val about: String?, val location: String?,
    val currentCompany: String?, val school: String?, val website: String?, val followers: String
)

private val PersonContent = HeaderContent(
    "Marie Dupont", "marie-dupont", "Directrice des soins, CHU de Nantes",
    "Je dirige la direction des soins du CHU de Nantes depuis 2019. Ancienne infirmière anesthésiste, je travaille " +
        "sur la qualité des parcours et la formation des équipes soignantes.",
    "Nantes, Pays de la Loire", "CHU de Nantes", "Université de Nantes", null, "1 204"
)

private val CompanyContent = HeaderContent(
    "CHU de Nantes", "chu-nantes", "Hospitals and Health Care",
    "Le CHU de Nantes est le premier employeur de la région. Il accueille chaque année plus de 700 000 patients dans " +
        "ses hôpitaux, forme les professionnels de santé de demain et conduit des programmes de recherche reconnus au " +
        "niveau international. Nos équipes sont engagées au quotidien pour l'accès aux soins de tous.",
    "Nantes, Pays de la Loire", null, null, "chu-nantes.fr", "49 851"
)

private val LongContent = HeaderContent(
    "Marie-Hélène Dupont-Lefebvre de la Roche-Saint-Aubin, Directrice générale adjointe",
    "marie-helene-dupont-lefebvre",
    "Directrice générale adjointe chargée de la qualité, de la sécurité des soins et de la gestion des risques, Centre " +
        "hospitalier universitaire de Nantes et groupement hospitalier de territoire Loire-Atlantique",
    "Depuis plus de vingt ans au service des patients et des équipes, sur trois sites et dans quatre régions. Je " +
        "travaille sur la qualité des parcours, la formation des professionnels, la gestion des risques et le dialogue " +
        "avec les usagers. Les opinions exprimées ici n'engagent que moi, et je réponds à chaque message.",
    "Nantes, Pays de la Loire, France, et déplacements fréquents dans toute la région Grand Ouest",
    "Centre hospitalier universitaire de Nantes", "École des hautes études en santé publique de Rennes", null,
    "12 345"
)

// The banner behind (PROFILE_BANNER_HEIGHT), the avatar at top 104 and left 10 inside its ring, the buttons row at the
// end below the banner (px_lg, pt_md), then the details at the start (px_lg).
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftHeader(following: Boolean, content: HeaderContent) {
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxWidth()) {
        // A stand-in for the banner picture: a vertical Brush of primary to tertiary, no Coil picture here.
        Box(
            Modifier.fillMaxWidth().height(ProfileBannerHeight)
                .semantics { contentDescription = "Banner" }
                .clickable(role = Role.Image, onClick = {})
                .background(
                    Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
                )
        )
        // Bluesky's StatusBarShadow: rgba(0,0,0,0.5) to transparent, the height of the inset.
        Box(
            Modifier.fillMaxWidth().height(inset).background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))
            )
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth()) {
                // GrowableAvatar: top 104, left 10, width 94, size 90: a ring of the page's colour around the avatar.
                Box(
                    Modifier.padding(top = AvatarTop, start = AvatarLeft)
                        .size(AvatarSize + AvatarRing * 2)
                        .background(MaterialTheme.colorScheme.background, CircleShape)
                        .clip(CircleShape)
                        .semantics { contentDescription = "Profile picture" }
                        .clickable(role = Role.Image, onClick = {}),
                    contentAlignment = Alignment.Center
                ) {
                    Avatar(url = null, name = content.name, size = AvatarSize)
                }
                // The buttons row: justify_end, px_lg 16, pt_md 12 below the banner.
                Row(
                    Modifier.align(Alignment.TopEnd).padding(top = ProfileBannerHeight + 8.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FollowButton(following = following, onClick = {})
                }
            }
            Spacer(Modifier.height(4.dp))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // DisplayName.tsx: the name wraps, capped here at 2 lines; a company's the same.
                    Text(
                        content.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    // The headline: one or two lines, a company's industry in the same place.
                    content.headline?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                ProfileHeaderMetrics(content)
                // The about text as the bio, four lines at most in the draft.
                content.about?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                if (content.location != null || content.currentCompany != null || content.school != null || content.website != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (content.location != null || content.currentCompany != null || content.school != null) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                content.location?.let { ProfileItem(LocationOnRounded, "Location", it) }
                                content.currentCompany?.let { ProfileItem(DomainRounded, "Company", it) }
                                content.school?.let { ProfileItem(SchoolRounded, "School", it) }
                            }
                        }
                        // A company's website, on its own row: one line, in the primary.
                        content.website?.let {
                            ProfileItem(
                                LinkRounded, "Website", it, MaterialTheme.colorScheme.primary,
                                Modifier.clickable {}, singleLine = true
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// https://github.com/TwidereProject/TwidereX-Android/blob/develop/common/src/commonMain/kotlin/com/twidere/twiderex/component/UserComponent.kt#ProfileItem
// an icon (16 dp here) then the text; one wrapping line.
@Composable
private fun ProfileItem(
    icon: ImageVector,
    description: String,
    text: String,
    textColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(4.dp))
        Text(
            text, style = MaterialTheme.typography.bodyMedium, color = textColor,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/ButtonMediumTokens.kt#ButtonMediumTokens
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/FilledButtonTokens.kt#FilledButtonTokens
// Follow as Material 3 Expressive's medium button with a leading icon, a clickable Row, no Material control, drawn as a
// glass zone (the app's rule, as the local AnimationFilmstripDraftScreenshotTest's follow and BoldButton): filled
// (primary, icon and label in onPrimary) or, once followed, glass only with the label and the check in the primary.
// not ported: ContainerShapeSquare and the Selected shapes (no toggle here), disabled and elevation tokens.
// not ported: textStyleFor (its one case here, titleMedium from the medium height up, is the Text's style below)
// not ported: the real FollowButton's animated container, line and content colours and AnimatedContent (a picture is
// one instant; the filled and followed states are two frames)
@Composable
private fun FollowButton(following: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val density = LocalDensity.current
    val height = ButtonMediumContainerHeight
    // ContainerShapeRound (CornerFull) morphs to PressedContainerShape (CornerMedium = ShapeDefaults.Medium) with a spring.
    val full = with(density) { height.toPx() / 2 }
    val medium = ShapeDefaults.Medium.topStart.toPx(Size(full * 2, full * 2), density)
    val corner by animateFloatAsState(
        targetValue = if (pressed) medium else full,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 380f),
        label = "followCorner"
    )
    val shape = RoundedCornerShape(with(density) { corner.toDp() })
    val contentColor = if (following) colors.primary else colors.onPrimary
    GlassTarget(Modifier) {
        Row(
            Modifier
                .heightIn(min = height)
                .then(if (glass != null) Modifier.glassZone(shape, glass, lens = 1f) else Modifier)
                .clip(shape)
                .then(if (following) Modifier else Modifier.background(colors.primary, shape))
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = contentColor),
                    role = Role.Button,
                    onClick = onClick
                )
                .padding(horizontal = ButtonMediumLeadingSpace),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (following) LinkedOutIcons.Check else PersonAddRounded,
                contentDescription = null,
                modifier = Modifier.size(ButtonMediumIconSize),
                tint = contentColor
            )
            Spacer(Modifier.width(ButtonMediumIconLabelSpace))
            // textStyleFor: titleMedium from the medium height up.
            Text(if (following) "Following" else "Follow", style = MaterialTheme.typography.titleMedium, color = contentColor)
        }
    }
}

// ButtonMediumTokens
private val ButtonMediumContainerHeight: Dp = 56.dp
private val ButtonMediumIconLabelSpace: Dp = 8.dp
private val ButtonMediumIconSize: Dp = 24.dp
private val ButtonMediumLeadingSpace: Dp = 24.dp

// https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/Metrics.tsx#ProfileHeaderMetrics
// the followers on one line (LinkedIn has no following or posts count on the guest card), a flow so 200 % wraps.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileHeaderMetrics(content: HeaderContent) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MetricsItem(content.followers, "followers")
    }
}

// https://github.com/bluesky-social/social-app/blob/main/src/screens/Profile/Header/Metrics.tsx#ProfileHeaderMetrics
// the count bold, its word in the contrast medium colour.
@Composable
private fun MetricsItem(primaryText: String, secondaryText: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$primaryText ") }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(secondaryText) }
        },
        style = MaterialTheme.typography.bodyMedium
    )
}

// layout.ts PROFILE_BANNER_HEIGHT, Shell.tsx GrowableAvatar (top 104, left 10, width 94, size 90)
private val ProfileBannerHeight: Dp = 150.dp
private val AvatarSize: Dp = 90.dp
private val AvatarRing: Dp = 2.dp
private val AvatarTop: Dp = 104.dp
private val AvatarLeft: Dp = 10.dp

private fun symbol(name: String, pathData: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, viewportWidth = 960f, viewportHeight = 960f)
        .addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.White))
        .build()

// Material Symbols Rounded "person_add".
// https://github.com/google/material-design-icons/blob/master/symbols/android/person_add/materialsymbolsrounded/person_add_24px.xml
private val PersonAddRounded: ImageVector by lazy {
    symbol(
        "PersonAdd",
        "M720,440L640,440Q623,440 611.5,428.5Q600,417 600,400Q600,383 611.5,371.5Q623,360 640,360L720,360L720,280" +
            "Q720,263 731.5,251.5Q743,240 760,240Q777,240 788.5,251.5Q800,263 800,280L800,360L880,360Q897,360 908.5,371.5" +
            "Q920,383 920,400Q920,417 908.5,428.5Q897,440 880,440L800,440L800,520Q800,537 788.5,548.5Q777,560 760,560" +
            "Q743,560 731.5,548.5Q720,537 720,520L720,440ZM360,480Q294,480 247,433Q200,386 200,320Q200,254 247,207" +
            "Q294,160 360,160Q426,160 473,207Q520,254 520,320Q520,386 473,433Q426,480 360,480ZM40,720L40,688" +
            "Q40,654 57.5,625.5Q75,597 104,582Q166,551 230,535.5Q294,520 360,520Q426,520 490,535.5Q554,551 616,582" +
            "Q645,597 662.5,625.5Q680,654 680,688L680,720Q680,753 656.5,776.5Q633,800 600,800L120,800Q87,800 63.5,776.5" +
            "Q40,753 40,720ZM120,720L600,720L600,688Q600,677 594.5,668Q589,659 580,654Q526,627 471,613.5Q416,600 360,600" +
            "Q304,600 249,613.5Q194,627 140,654Q131,659 125.5,668Q120,677 120,688L120,720ZM360,400Q393,400 416.5,376.5" +
            "Q440,353 440,320Q440,287 416.5,263.5Q393,240 360,240Q327,240 303.5,263.5Q280,287 280,320Q280,353 303.5,376.5" +
            "Q327,400 360,400Z"
    )
}

// Material Symbols Rounded "location_on".
// https://github.com/google/material-design-icons/blob/master/symbols/android/location_on/materialsymbolsrounded/location_on_24px.xml
private val LocationOnRounded: ImageVector by lazy {
    symbol(
        "LocationOn",
        "M480,774Q602,662 661,570.5Q720,479 720,408Q720,299 650.5,229.5Q581,160 480,160Q379,160 309.5,229.5Q240,299 240,408" +
            "Q240,479 299,570.5Q358,662 480,774ZM480,853Q466,853 452,848Q438,843 427,833Q362,773 312,716Q262,659 228.5,605.5" +
            "Q195,552 177.5,502.5Q160,453 160,408Q160,258 256.5,169Q353,80 480,80Q607,80 703.5,169Q800,258 800,408" +
            "Q800,453 782.5,502.5Q765,552 731.5,605.5Q698,659 648,716Q598,773 533,833Q522,843 508,848Q494,853 480,853Z" +
            "M480,480Q513,480 536.5,456.5Q560,433 560,400Q560,367 536.5,343.5Q513,320 480,320Q447,320 423.5,343.5" +
            "Q400,367 400,400Q400,433 423.5,456.5Q447,480 480,480Z"
    )
}

// Material Symbols Rounded "domain" (Material Icons' "business").
// https://github.com/google/material-design-icons/blob/master/symbols/android/domain/materialsymbolsrounded/domain_24px.xml
private val DomainRounded: ImageVector by lazy {
    symbol(
        "Domain",
        "M80,760L80,200Q80,167 103.5,143.5Q127,120 160,120L400,120Q433,120 456.5,143.5Q480,167 480,200L480,280L800,280" +
            "Q833,280 856.5,303.5Q880,327 880,360L880,760Q880,793 856.5,816.5Q833,840 800,840L160,840Q127,840 103.5,816.5" +
            "Q80,793 80,760ZM160,760L240,760L240,680L160,680L160,760ZM160,600L240,600L240,520L160,520L160,600ZM160,440" +
            "L240,440L240,360L160,360L160,440ZM160,280L240,280L240,200L160,200L160,280ZM320,760L400,760L400,680L320,680" +
            "L320,760ZM320,600L400,600L400,520L320,520L320,600ZM320,440L400,440L400,360L320,360L320,440ZM320,280L400,280" +
            "L400,200L320,200L320,280ZM480,760L800,760L800,360L480,360L480,440L560,440L560,520L480,520L480,600L560,600" +
            "L560,680L480,680L480,760ZM640,520L640,440L720,440L720,520L640,520ZM640,680L640,600L720,600L720,680L640,680Z"
    )
}

// Material Symbols Rounded "school".
// https://github.com/google/material-design-icons/blob/master/symbols/android/school/materialsymbolsrounded/school_24px.xml
private val SchoolRounded: ImageVector by lazy {
    symbol(
        "School",
        "M242,711Q222,700 211,681.5Q200,663 200,640L200,448L104,395Q93,389 88,380Q83,371 83,360Q83,349 88,340Q93,331 104,325" +
            "L442,141Q451,136 460.5,133.5Q470,131 480,131Q490,131 499.5,133.5Q509,136 518,141L899,349Q909,354 914.5,363.5" +
            "Q920,373 920,384L920,640Q920,657 908.5,668.5Q897,680 880,680Q863,680 851.5,668.5Q840,657 840,640L840,404L760,448" +
            "L760,640Q760,663 749,681.5Q738,700 718,711L518,819Q509,824 499.5,826.5Q490,829 480,829Q470,829 460.5,826.5" +
            "Q450,824 442,819L242,711ZM480,508Q480,508 480,508Q480,508 480,508L754,360L480,212Q480,212 480,212Q480,212 480,212" +
            "L206,360L480,508ZM480,749Q480,749 480,749Q480,749 480,749L680,641Q680,641 680,641Q680,641 680,641L680,490" +
            "L519,579Q510,584 500,586.5Q490,589 480,589Q470,589 460,586.5Q450,584 441,579L280,490L280,641Q280,641 280,641" +
            "Q280,641 280,641L480,749ZM480,508L480,508L480,508Q480,508 480,508Q480,508 480,508L480,508L480,508Q480,508 480,508" +
            "Q480,508 480,508ZM480,629Q480,629 480,629Q480,629 480,629L480,629L480,629L480,629L480,629L480,629L480,629L480,629" +
            "Q480,629 480,629Q480,629 480,629L480,629ZM480,629L480,629Q480,629 480,629Q480,629 480,629L480,629L480,629L480,629" +
            "L480,629L480,629L480,629L480,629Q480,629 480,629Q480,629 480,629L480,629Z"
    )
}

// Material Symbols Rounded "link".
// https://github.com/google/material-design-icons/blob/master/symbols/android/link/materialsymbolsrounded/link_24px.xml
private val LinkRounded: ImageVector by lazy {
    symbol(
        "Link",
        "M280,680Q197,680 138.5,621.5Q80,563 80,480Q80,397 138.5,338.5Q197,280 280,280L400,280Q417,280 428.5,291.5" +
            "Q440,303 440,320Q440,337 428.5,348.5Q417,360 400,360L280,360Q230,360 195,395Q160,430 160,480Q160,530 195,565" +
            "Q230,600 280,600L400,600Q417,600 428.5,611.5Q440,623 440,640Q440,657 428.5,668.5Q417,680 400,680L280,680Z" +
            "M360,520Q343,520 331.5,508.5Q320,497 320,480Q320,463 331.5,451.5Q343,440 360,440L600,440Q617,440 628.5,451.5" +
            "Q640,463 640,480Q640,497 628.5,508.5Q617,520 600,520L360,520ZM560,680Q543,680 531.5,668.5Q520,657 520,640" +
            "Q520,623 531.5,611.5Q543,600 560,600L680,600Q730,600 765,565Q800,530 800,480Q800,430 765,395Q730,360 680,360" +
            "L560,360Q543,360 531.5,348.5Q520,337 520,320Q520,303 531.5,291.5Q543,280 560,280L680,280Q763,280 821.5,338.5" +
            "Q880,397 880,480Q880,563 821.5,621.5Q763,680 680,680L560,680Z"
    )
}
