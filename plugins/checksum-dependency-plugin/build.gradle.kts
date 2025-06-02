/*
 * Copyright 2019 Vladimir Sitnikov <sitnikov.vladimir@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

dependencies {
    implementation("org.bouncycastle:bcpg-jdk15on:1.70")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

fun File.sha512(): String {
    val md = `java.security`.MessageDigest.getInstance("SHA-512")
    forEachBlock { buffer, bytesRead ->
        md.update(buffer, 0, bytesRead)
    }
    return BigInteger(1, md.digest()).toString(16).toUpperCase()
}

tasks {
    jar {
        doLast {
            for (f in outputs.files) {
                println("SHA-512(${f.name}) = ${f.sha512()}")
            }
        }
    }
}
