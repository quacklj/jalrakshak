package com.example.jalraksha.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.jalraksha.ui.main.MainScreen
import com.example.jalraksha.ui.language.LanguageScreen
import com.example.jalraksha.ui.signin.SignInScreen
import com.example.jalraksha.ui.village.VillageScreen

/** The four screens of the design, in the order it numbers them. */
object Routes {
    const val SIGN_IN = "signIn"
    const val LANGUAGE = "language"
    const val VILLAGE = "village"
    /** Screens 04–07, behind one persistent bottom bar. */
    const val MAIN = "main"
}

@Composable
fun JalrakshaNavHost(
    startDestination: String,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.SIGN_IN) {
            SignInScreen(
                onSignedIn = {
                    navController.navigate(Routes.LANGUAGE) {
                        // Signing in is a one-way door — back from onboarding should leave the app,
                        // not return to a form that would sign you in again.
                        popUpTo(Routes.SIGN_IN) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.LANGUAGE) {
            LanguageScreen(
                onContinue = { navController.navigate(Routes.VILLAGE) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.VILLAGE) {
            VillageScreen(
                onConfirmed = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.LANGUAGE) { inclusive = true }
                    }
                    },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.MAIN) {
            MainScreen(
                onNoVillage = {
                    navController.navigate(Routes.VILLAGE) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                },
                onSignedOut = {
                    navController.navigate(Routes.SIGN_IN) {
                        // Signing out clears the whole stack — Back must not walk into a
                        // dashboard belonging to an account that is no longer signed in.
                        popUpTo(0) { inclusive = true }
                    }
                },
                // Both settings rows send the user back through the onboarding step that owns
                // them, so there is one place each answer is changed.
                onEditLanguage = { navController.navigate(Routes.LANGUAGE) },
                onEditVillage = { navController.navigate(Routes.VILLAGE) },
            )
        }
    }
}
