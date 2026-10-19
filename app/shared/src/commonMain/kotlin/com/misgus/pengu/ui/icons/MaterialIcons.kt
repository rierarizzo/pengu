package com.misgus.pengu.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Icon set vendored from Material Symbols Sharp (Apache-2.0, Google).
 *
 * The whole UI uses these glyphs; call sites size them with
 * [com.misgus.pengu.ui.theme.AppIconSize]. `scripts/gen_icons.mjs` regenerates
 * the file from the `@material-symbols/svg-400` package. Only the glyphs the UI
 * uses are kept, and each pair (a filled and an outline glyph, e.g. check circle
 * and its outline) mirrors the solid/outline split the UI had before.
 */
object MaterialIcons {
    val Home: ImageVector by lazy { material("Home", HOME) }
    val Search: ImageVector by lazy { material("Search", SEARCH) }
    val Bookmark: ImageVector by lazy { material("Bookmark", BOOKMARK) }
    val Calendar: ImageVector by lazy { material("Calendar", CALENDAR) }
    val Settings: ImageVector by lazy { material("Settings", SETTINGS) }
    val Play: ImageVector by lazy { material("Play", PLAY) }
    val Plus: ImageVector by lazy { material("Plus", PLUS) }
    val Check: ImageVector by lazy { material("Check", CHECK) }
    val CheckCircle: ImageVector by lazy { material("CheckCircle", CHECK_CIRCLE) }
    val CheckCircleOutline: ImageVector by lazy { material("CheckCircleOutline", CHECK_CIRCLE_OUTLINE) }
    val ArrowBack: ImageVector by lazy { material("ArrowBack", ARROW_BACK, autoMirror = true) }
    val ChevronLeft: ImageVector by lazy { material("ChevronLeft", CHEVRON_LEFT) }
    val ChevronRight: ImageVector by lazy { material("ChevronRight", CHEVRON_RIGHT) }
    val ChevronDown: ImageVector by lazy { material("ChevronDown", CHEVRON_DOWN) }
    val Close: ImageVector by lazy { material("Close", CLOSE) }
    val Pause: ImageVector by lazy { material("Pause", PAUSE) }
    val Refresh: ImageVector by lazy { material("Refresh", REFRESH) }
    val Forward: ImageVector by lazy { material("Forward", FORWARD) }
    val Replay: ImageVector by lazy { material("Replay", REPLAY) }
    val Fullscreen: ImageVector by lazy { material("Fullscreen", FULLSCREEN) }
    val Volume: ImageVector by lazy { material("Volume", VOLUME) }
    val Visibility: ImageVector by lazy { material("Visibility", VISIBILITY) }
    val VisibilityOff: ImageVector by lazy { material("VisibilityOff", VISIBILITY_OFF) }
    val Subtitles: ImageVector by lazy { material("Subtitles", SUBTITLES) }
    val Audiotrack: ImageVector by lazy { material("Audiotrack", AUDIOTRACK) }
    val Error: ImageVector by lazy { material("Error", ERROR) }
    val Compass: ImageVector by lazy { material("Compass", COMPASS) }
    val FolderOpen: ImageVector by lazy { material("FolderOpen", FOLDER_OPEN) }
    val Inbox: ImageVector by lazy { material("Inbox", INBOX) }
    val Movie: ImageVector by lazy { material("Movie", MOVIE) }
    val User: ImageVector by lazy { material("User", USER) }
    val SearchOff: ImageVector by lazy { material("SearchOff", SEARCH_OFF) }
    val CalendarMonth: ImageVector by lazy { material("CalendarMonth", CALENDAR_MONTH) }
    val Copy: ImageVector by lazy { material("Copy", COPY) }
    val Login: ImageVector by lazy { material("Login", LOGIN, autoMirror = true) }
    val Logout: ImageVector by lazy { material("Logout", LOGOUT, autoMirror = true) }
}

/**
 * Builds one [ImageVector] from its Material Symbols path.
 *
 * The glyphs live on a 960x960 grid whose y axis runs from -960 to 0, so the
 * path is wrapped in a group translated by +960; the vector still renders at
 * the 24dp the call sites ask for. `autoMirror` flips the directional glyphs
 * (the back arrow and sign-in/out) in RTL.
 */
private fun material(
    name: String,
    pathData: String,
    autoMirror: Boolean = false,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
            autoMirror = autoMirror,
        ).apply {
            addGroup(translationY = 960f)
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()

private const val HOME =
    "M160-120v-480l320-240 320 240v480H560v-280H400v280H160Z"
private const val SEARCH =
    "M796-121 533-384q-30 26-70 40.5T378-329q-108 0-183-75t-75-181q0-106 75-181t182-75q106 0 180.5 75T632-585q0 43-" +
        "14 83t-42 75l264 262-44 44ZM377-389q81 0 138-57.5T572-585q0-81-57-138.5T377-781q-82 0-139.5 57.5T180-585q0 81 " +
        "57.5 138.5T377-389Z"
private const val BOOKMARK =
    "M200-120v-725h560v725L480-240 200-120Z"
private const val CALENDAR =
    "M440-400v-80h80v80h-80Zm-160 0v-80h80v80h-80Zm320 0v-80h80v80h-80ZM440-240v-80h80v80h-80Zm-160 0v-80h80v80h-80" +
        "Zm320 0v-80h80v80h-80ZM120-80v-740h125v-60h65v60h340v-60h65v60h125v740H120Zm60-60h600v-430H180v430Z"
private const val SETTINGS =
    "m388-80-20-126q-19-7-40-19t-37-25l-118 54-93-164 108-79q-2-9-2.5-20.5T185-480q0-9 .5-20.5T188-521L80-600l93-16" +
        "4 118 54q16-13 37-25t40-18l20-127h184l20 126q19 7 40.5 18.5T669-710l118-54 93 164-108 77q2 10 2.5 21.5t.5 21.5" +
        "q0 10-.5 21t-2.5 21l108 78-93 164-118-54q-16 13-36.5 25.5T592-206L572-80H388Zm92-270q54 0 92-38t38-92q0-54-38-" +
        "92t-92-38q-54 0-92 38t-38 92q0 54 38 92t92 38Z"
private const val PLAY =
    "M320-203v-560l440 280-440 280Z"
private const val PLUS =
    "M450-450H200v-60h250v-250h60v250h250v60H510v250h-60v-250Z"
private const val CHECK =
    "M378-246 154-470l43-43 181 181 384-384 43 43-427 427Z"
private const val CHECK_CIRCLE =
    "m421-298 283-283-46-45-237 237-120-120-45 45 165 166Zm59 218q-82 0-155-31.5t-127.5-86Q143-252 111.5-325T80-480" +
        "q0-83 31.5-156t86-127Q252-817 325-848.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 82-31.5 155T763" +
        "-197.5q-54 54.5-127 86T480-80Z"
private const val CHECK_CIRCLE_OUTLINE =
    "m421-298 283-283-46-45-237 237-120-120-45 45 165 166Zm59 218q-82 0-155-31.5t-127.5-86Q143-252 111.5-325T80-480" +
        "q0-83 31.5-156t86-127Q252-817 325-848.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 82-31.5 155T763" +
        "-197.5q-54 54.5-127 86T480-80Zm0-60q142 0 241-99.5T820-480q0-142-99-241t-241-99q-141 0-240.5 99T140-480q0 141 " +
        "99.5 240.5T480-140Zm0-340Z"
private const val ARROW_BACK =
    "m274-450 248 248-42 42-320-320 320-320 42 42-248 248h526v60H274Z"
private const val CHEVRON_LEFT =
    "M561-240 320-481l241-241 43 43-198 198 198 198-43 43Z"
private const val CHEVRON_RIGHT =
    "M530-481 332-679l43-43 241 241-241 241-43-43 198-198Z"
private const val CHEVRON_DOWN =
    "M480-344 240-584l43-43 197 197 197-197 43 43-240 240Z"
private const val CLOSE =
    "m249-207-42-42 231-231-231-231 42-42 231 231 231-231 42 42-231 231 231 231-42 42-231-231-231 231Z"
private const val PAUSE =
    "M525-200v-560h235v560H525Zm-325 0v-560h235v560H200Zm385-60h115v-440H585v440Zm-325 0h115v-440H260v440Zm0-440v44" +
        "0-440Zm325 0v440-440Z"
private const val REFRESH =
    "M480-160q-133 0-226.5-93.5T160-480q0-133 93.5-226.5T480-800q85 0 149 34.5T740-671v-129h60v254H546v-60h168q-38-" +
        "60-97-97t-137-37q-109 0-184.5 75.5T220-480q0 109 75.5 184.5T480-220q83 0 152-47.5T728-393h62q-29 105-115 169t-" +
        "195 64Z"
private const val FORWARD =
    "M104-240v-480l346 240-346 240Zm407 0v-480l346 240-346 240ZM164-480Zm407 0ZM164-355l181-125-181-125v250Zm407 0 " +
        "181-125-181-125v250Z"
private const val REPLAY =
    "M339.5-108Q274-136 225-185t-77-114.5Q120-365 120-440h60q0 125 87.5 212.5T480-140q125 0 212.5-87.5T780-440q0-12" +
        "5-85-212.5T485-740h-23l73 73-41 42-147-147 147-147 41 41-78 78h23q75 0 140.5 28T735-695q49 49 77 114.5T840-440" +
        "q0 75-28 140.5T735-185q-49 49-114.5 77T480-80q-75 0-140.5-28Z"
private const val FULLSCREEN =
    "M120-120v-193h60v133h133v60H120Zm527 0v-60h133v-133h60v193H647ZM120-647v-193h193v60H180v133h-60Zm660 0v-133H64" +
        "7v-60h193v193h-60Z"
private const val VOLUME =
    "M560-131v-62q97-28 158.5-107.5T780-481q0-101-61-181T560-769v-62q124 28 202 125.5T840-481q0 127-78 224.5T560-13" +
        "1ZM120-360v-240h160l200-200v640L280-360H120Zm420 48v-337q55 17 87.5 64T660-480q0 57-33 104t-87 64ZM420-648 307" +
        "-540H180v120h127l113 109v-337Zm-94 168Z"
private const val VISIBILITY =
    "M600.5-379.5Q650-429 650-500t-49.5-120.5Q551-670 480-670t-120.5 49.5Q310-571 310-500t49.5 120.5Q409-330 480-33" +
        "0t120.5-49.5Zm-200-41Q368-453 368-500t32.5-79.5Q433-612 480-612t79.5 32.5Q592-547 592-500t-32.5 79.5Q527-388 4" +
        "80-388t-79.5-32.5ZM216-283Q98-366 40-500q58-134 176-217t264-83q146 0 264 83t176 217q-58 134-176 217t-264 83q-1" +
        "46 0-264-83Zm264-217Zm222.5 174.5Q804-391 857-500q-53-109-154.5-174.5T480-740q-121 0-222.5 65.5T102-500q54 109" +
        " 155.5 174.5T480-260q121 0 222.5-65.5Z"
private const val VISIBILITY_OFF =
    "m629-419-44-44q26-71-27-118t-115-24l-44-44q17-11 38-16t43-5q71 0 120.5 49.5T650-500q0 22-5.5 43.5T629-419Zm129" +
        " 129-40-40q49-36 85.5-80.5T857-500q-50-111-150-175.5T490-740q-42 0-86 8t-69 19l-46-47q35-16 89.5-28T485-800q14" +
        "3 0 261.5 81.5T920-500q-26 64-67 117t-95 93Zm58 226L648-229q-35 14-79 21.5t-89 7.5q-146 0-265-81.5T40-500q20-5" +
        "2 55.5-101.5T182-696L56-822l42-43 757 757-39 44ZM223-654q-37 27-71.5 71T102-500q51 111 153.5 175.5T488-260q33 " +
        "0 65-4t48-12l-64-64q-11 5-27 7.5t-30 2.5q-70 0-120-49t-50-121q0-15 2.5-30t7.5-27l-97-97Zm305 142Zm-116 58Z"
private const val SUBTITLES =
    "M80-160v-640h800v640H80Zm60-60h680v-520H140v520Zm0 0v-520 520Zm100-130h360v-60H240v60Zm420 0h60v-60h-60v60ZM24" +
        "0-470h60v-60h-60v60Zm120 0h360v-60H360v60Z"
private const val AUDIOTRACK =
    "M286.5-163.5Q243-207 243-270t43.5-106.5Q330-420 393-420q28 0 50.5 8t39.5 22v-450h234v135H543v435q0 63-43.5 106" +
        ".5T393-120q-63 0-106.5-43.5Z"
private const val ERROR =
    "M503.5-289.48q9.5-9.48 9.5-23.5t-9.48-23.52q-9.48-9.5-23.5-9.5t-23.52 9.48q-9.5 9.48-9.5 23.5t9.48 23.52q9.48 " +
        "9.5 23.5 9.5t23.52-9.48ZM453-433h60v-253h-60v253Zm27.27 353q-82.74 0-155.5-31.5Q252-143 197.5-197.5t-86-127.34" +
        "Q80-397.68 80-480.5t31.5-155.66Q143-709 197.5-763t127.34-85.5Q397.68-880 480.5-880t155.66 31.5Q709-817 763-763" +
        "t85.5 127Q880-563 880-480.27q0 82.74-31.5 155.5Q817-252 763-197.68q-54 54.31-127 86Q563-80 480.27-80Zm.23-60Q6" +
        "22-140 721-239.5t99-241Q820-622 721.19-721T480-820q-141 0-240.5 98.81T140-480q0 141 99.5 240.5t241 99.5Zm-.5-3" +
        "40Z"
private const val COMPASS =
    "m303-303 270-83 83-270-270 83-83 270Zm176.76-137q-16.76 0-28.26-11.74-11.5-11.73-11.5-28.5 0-16.76 11.74-28.26" +
        " 11.73-11.5 28.5-11.5 16.76 0 28.26 11.74 11.5 11.73 11.5 28.5 0 16.76-11.74 28.26-11.73 11.5-28.5 11.5Zm.51 3" +
        "60q-82.74 0-155.5-31.5Q252-143 197.5-197.5t-86-127.34Q80-397.68 80-480.5t31.5-155.66Q143-709 197.5-763t127.34-" +
        "85.5Q397.68-880 480.5-880t155.66 31.5Q709-817 763-763t85.5 127Q880-563 880-480.27q0 82.74-31.5 155.5Q817-252 7" +
        "63-197.68q-54 54.31-127 86Q563-80 480.27-80Zm.22-60Q622-140 721-239.49q99-99.48 99-241Q820-622 721-721t-240.51" +
        "-99q-141.52 0-241 99Q140-622 140-480.49q0 141.52 99.49 241 99.48 99.49 241 99.49ZM480-480Z"
private const val FOLDER_OPEN =
    "M80-160v-640h341l60 60h399v60H455l-60-60H140v520l102-400h698L821-160H80Zm123-60h572l84-340H287l-84 340Zm0 0 84" +
        "-340-84 340Zm-63-460v-60 60Z"
private const val INBOX =
    "M120-120v-720h720v720H120Zm60-60h600v-136H634q-26 40-67.5 61.5T480-233q-45 0-86.5-21.5T326-316H180v136Zm374-13" +
        "6.5q33-23.5 56-59.5h170v-404H180v404h170q23 36 56 59.5t74 23.5q41 0 74-23.5ZM180-180h600-600Z"
private const val MOVIE =
    "M80-160v-640h60l74 152h130l-74-152h89l74 152h130l-74-152h89l74 152h130l-74-152h172v640H80Zm60-428v368h680v-368" +
        "H140Zm0 0v368-368Z"
private const val USER =
    "M372-523q-42-42-42-108t42-108q42-42 108-42t108 42q42 42 42 108t-42 108q-42 42-108 42t-108-42ZM160-160v-94q0-38" +
        " 19-65t49-41q67-30 128.5-45T480-420q62 0 123 15.5T731-360q31 14 50 41t19 65v94H160Zm60-60h520v-34q0-16-9.5-30." +
        "5T707-306q-64-31-117-42.5T480-360q-57 0-111 11.5T252-306q-14 7-23 21.5t-9 30.5v34Zm324.5-346.5Q570-592 570-631" +
        "t-25.5-64.5Q519-721 480-721t-64.5 25.5Q390-670 390-631t25.5 64.5Q441-541 480-541t64.5-25.5ZM480-631Zm0 411Z"
private const val SEARCH_OFF =
    "M136-136q-56-56-56-134t56-134q56-56 134-56t134 56q56 56 56 134t-56 134q-56 56-134 56t-134-56Zm700 16L573-383q-" +
        "14 11-31.5 21.5T508-344q-5-14-11-28.5T483-399q54-21 91.5-69.5T612-584q0-81-57-138.5T417-780q-82 0-139.5 57.5T2" +
        "20-584q0 17 3.5 35.5T232-517q-13 2-29 6.5T174-500q-7-18-10.5-40t-3.5-44q0-107 75-181.5T417-840q106 0 180.5 75T" +
        "672-584q0 43-15 85t-41 73l264 262-44 44Zm-635-56 69-69 68 68 23-23-69-69 71-71-23-23-70 70-70-70-23 23 70 70-7" +
        "0 70 24 24Z"
private const val CALENDAR_MONTH =
    "M440-400v-80h80v80h-80Zm-160 0v-80h80v80h-80Zm320 0v-80h80v80h-80ZM440-240v-80h80v80h-80Zm-160 0v-80h80v80h-80" +
        "Zm320 0v-80h80v80h-80ZM120-80v-740h125v-60h65v60h340v-60h65v60h125v740H120Zm60-60h600v-430H180v430Zm0-490h600v" +
        "-130H180v130Zm0 0v-130 130Z"
private const val COPY =
    "M240-200v-680h560v680H240Zm60-60h440v-560H300v560ZM120-80v-680h60v620h500v60H120Zm180-180v-560 560Z"
private const val LOGIN =
    "M481-120v-60h299v-600H481v-60h359v720H481Zm-55-185-43-43 102-102H120v-60h363L381-612l43-43 176 176-174 174Z"
private const val LOGOUT =
    "M120-120v-720h359v60H180v600h299v60H120Zm546-185-43-43 102-102H360v-60h363L621-612l43-43 176 176-174 174Z"
