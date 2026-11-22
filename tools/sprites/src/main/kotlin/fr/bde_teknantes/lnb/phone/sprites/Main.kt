package fr.bde_teknantes.lnb.phone.sprites

import java.io.File
import javax.imageio.ImageIO
import kotlin.system.exitProcess

// Run through Gradle: `./gradlew :tools:sprites:generateSprites` (writes the assets) and
// `./gradlew :tools:sprites:contactSheet -Psheet=<file>` (writes a sheet for checking by eye).
//
//   --out <dir>          assets/lnbphone/textures/gui of the phone; the sprites go under it
//   --sheet <file>       a contact sheet PNG of the whole set
//   --px-per-dp <n>      density of the smooth set (default 2, the art direction's)

fun allSprites(format: SmoothFormat = SmoothFormat()): List<Sprite> = shellSprites() + smoothSprites(format)

fun main(args: Array<String>) {
    val options = args.toList().chunked(2).associate { pair ->
        if (pair.size != 2 || !pair[0].startsWith("--")) usage()
        pair[0].removePrefix("--") to pair[1]
    }
    val format = SmoothFormat(pxPerDp = options["px-per-dp"]?.toDouble() ?: 2.0)
    val out = options["out"]
    val sheet = options["sheet"]
    if (out == null && sheet == null) usage()

    val shell = shellSprites()
    val smooth = smoothSprites(format)
    if (out != null) {
        val results = writeSprites(File(out), shell + smooth)
        results.filterValues { it != WriteResult.UNCHANGED }.toSortedMap().forEach { (path, result) ->
            println("${result.name.lowercase()}  $path.png")
        }
        val unchanged = results.count { it.value == WriteResult.UNCHANGED }
        println("${results.size} sprites in $out, $unchanged unchanged")
    }
    if (sheet != null) {
        val file = File(sheet)
        file.absoluteFile.parentFile.mkdirs()
        ImageIO.write(contactSheet(shell, smooth, format), "png", file)
        println("Contact sheet: ${file.absolutePath}")
    }
}

private fun usage(): Nothing {
    System.err.println("Usage: [--out <textures/gui dir>] [--sheet <png>] [--px-per-dp <n>]")
    exitProcess(2)
}
