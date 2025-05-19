import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

val start = "<artifact>\n" +
        "<id>"
val end = "</id>\n" +
        "<override>true</override>\n" +
        "<source>true</source>\n" +
        "<instructions>\n" +
        "<_noee>true</_noee>\n" +
        "</instructions>\n" +
        "</artifact>\n"

val prefix = "[INFO]    "

val file = File("./deps.txt")
    try {
        BufferedReader(FileReader(file)).use { br ->
            var line: String
            while (br.readLine().also { line = it } != null) {
                if (line.startsWith(prefix)) {
                    line = line.substring(prefix.length);
                }
                var i = line.indexOf(":compile")
                if (i < 0) {
                    i = line.indexOf(":runtime")
                }
                if (i > 0 && !line.isBlank()) {
                    line = line.substring(0, i)
                    print(start + line + end)
                }
            }
        }
    } catch (e: IOException) {
        e.printStackTrace()
    }
