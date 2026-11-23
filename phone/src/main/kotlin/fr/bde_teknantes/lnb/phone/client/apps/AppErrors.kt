package fr.bde_teknantes.lnb.phone.client.apps

/**
 * Lets through what isn't an app's fault: out of memory and the like are the game's problem. Everything else from an
 * app's code (a stack overflow, a NoSuchMethodError from a jar built against an older phone) is the app's, and the
 * phone catches it.
 */
internal fun rethrowIfFatal(e: Throwable) {
    if (e is VirtualMachineError && e !is StackOverflowError) throw e
}
