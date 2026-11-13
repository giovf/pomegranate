package com.gankdat.pomegranate.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.gankdat.pomegranate.PomegranateApplication
import com.gankdat.pomegranate.R
import com.gankdat.pomegranate.ui.navigation.PomegranateScaffold

/**
 * Detail view opened from the library: one page per item of the folder or
 * month the user was looking at, swiped sideways. Each page is the same
 * content as the stand-alone detail screens.
 */
@Composable
internal fun MediaPagerRoute(navController: NavHostController, startIndex: Int) {
    val app = PomegranateApplication.from(LocalContext.current)
    val refs = remember { app.browseSession.entries }
    if (refs.isEmpty()) {
        // Process was restarted and the list is gone: go back to the library.
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, refs.lastIndex)) { refs.size }
    val current = refs[pagerState.currentPage.coerceIn(0, refs.lastIndex)]
    // Same key as the page below, so this is the page's own ViewModel; it feeds the share action.
    val currentDeviceItem = current.deviceItemId?.let { id ->
        val state by itemDetailViewModel(id).state.collectAsStateWithLifecycle()
        (state as? ItemDetailUiState.Success)?.item
    }

    PomegranateScaffold(
        navController = navController,
        title = current.displayName.ifBlank { stringResource(R.string.title_item_detail) },
        actions = { if (currentDeviceItem != null) ShareAction(currentDeviceItem) },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            key = { refs[it].key },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { page ->
            val ref = refs[page]
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    ref.deviceItemId != null -> ItemDetailBody(itemDetailViewModel(ref.deviceItemId), navController)
                    ref.cloudItemId != null -> CloudItemDetailBody(cloudItemDetailViewModel(ref.cloudItemId), navController)
                }
            }
        }
    }
}
