# Project-specific R8 keep rules.
#
# Deliberately empty. Everything that needs keeping is covered elsewhere:
# - Room, WorkManager, DataStore and Compose ship consumer rules in their AARs.
# - Activities (MainActivity, auth.OAuthRedirectActivity) and the Application
#   class are kept because they are referenced from AndroidManifest.xml.
# - Room generates Kotlin code at build time; no reflection on entities.
# - The app's own JSON reader works on Maps, so nothing is reflected into.
# Add a rule here only with a comment naming the class and the stack trace
# that proved it was stripped or renamed.
