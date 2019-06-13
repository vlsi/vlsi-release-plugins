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
package com.github.vlsi.gradle.release

import com.github.vlsi.gradle.license.api.SimpleLicense
import java.net.URI

object ExtraLicense {
    val Indiana_University_1_1_1 =
        SimpleLicense(
            "Indiana University Extreme! Lab Software License, version 1.1.1",
            URI("http://www.extreme.indiana.edu/viewcvs/~checkout~/XPP3/java/LICENSE.txt")
        )
}
