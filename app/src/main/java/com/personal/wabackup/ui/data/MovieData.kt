package com.personal.wabackup.ui.data

import com.personal.wabackup.ui.model.Movie
import com.personal.wabackup.ui.model.MovieSection

object MovieData {

    const val TMDB_BASE = "https://image.tmdb.org/t/p/w342"
    const val TMDB_BACKDROP = "https://image.tmdb.org/t/p/w780"

    /** 5 featured shows that rotate in the hero banner */
    val featured = listOf(
        Movie("Stranger Things", "2022", "/49WJfeN0moxb9IPfGn8AIqMGskD.jpg", "99% Match", "Sci-Fi \u2022 Horror \u2022 Drama"),
        Movie("Squid Game", "2021", "/dDlEmu3EZ0Pgg93K2SVNLCjCSvE.jpg", "99% Match", "Thriller \u2022 Drama"),
        Movie("The Crown", "2022", "/voHsLwH9sXKbg6gQ3HwVqMVs1eM.jpg", "95% Match", "Drama \u2022 History"),
        Movie("Money Heist", "2021", "/reEMJA1uzscCbkpeRJeTT2bjqUp.jpg", "98% Match", "Action \u2022 Crime"),
        Movie("Ozark", "2022", "/4EYPN5mVIhKLfxGruy7Dy41dTVn.jpg", "97% Match", "Crime \u2022 Thriller")
    )

    /** Backdrop paths for hero (landscape, wider images) */
    val featuredBackdrops = listOf(
        "/56v2KjBlU4XaOv9rVYEQypROD7P.jpg",
        "/qw3J9cNeLioOLor68WX7z79eThu.jpg",
        "/zyPTHGjzM0mVOCLUTHxCmKWvKYH.jpg",
        "/piXnFwGSjXn9TLrVOdkPTSUBBqh.jpg",
        "/jLCxbEG5bMoEo78rAyiPvFdHULb.jpg"
    )

    val sections = listOf(
        MovieSection("Trending Now", listOf(
            Movie("Stranger Things", "2022", "/49WJfeN0moxb9IPfGn8AIqMGskD.jpg", "99% Match", "Sci-Fi"),
            Movie("Squid Game", "2021", "/dDlEmu3EZ0Pgg93K2SVNLCjCSvE.jpg", "99% Match", "Thriller"),
            Movie("The Crown", "2022", "/voHsLwH9sXKbg6gQ3HwVqMVs1eM.jpg", "95% Match", "Drama"),
            Movie("Bridgerton", "2022", "/luoKpgVwi1E5nQsi7W0UuKHu2Rq.jpg", "96% Match", "Romance"),
            Movie("Emily in Paris", "2022", "/AuGiPiGMYMkSosOJ9BQdYrqnVkK.jpg", "88% Match", "Comedy"),
            Movie("The Umbrella Academy", "2022", "/scZlQQYnDVlnpxFTxaIv2g0BWnL.jpg", "94% Match", "Sci-Fi"),
            Movie("Lucifer", "2021", "/ekZobS8isE6mA53RAiGDG93hBxL.jpg", "97% Match", "Fantasy"),
            Movie("You", "2021", "/7yaEuBHdqJmGTvSd2SiSlOPSEPV.jpg", "93% Match", "Thriller"),
            Movie("Sex Education", "2023", "/sNoKMslxPEJVwAV3WJHZ0t97g04.jpg", "91% Match", "Comedy"),
            Movie("The Witcher", "2021", "/7vjaCdMw15FEbXyLQTVa04URsPm.jpg", "91% Match", "Fantasy"),
            Movie("Dark", "2020", "/apbrbWs5M9e2ngfDFgEGJ5G7u5J.jpg", "94% Match", "Sci-Fi"),
            Movie("Ozark", "2022", "/4EYPN5mVIhKLfxGruy7Dy41dTVn.jpg", "97% Match", "Crime")
        )),
        MovieSection("Netflix Originals", listOf(
            Movie("The Queen's Gambit", "2020", "/zU0htwkhNvBQdVSIKB9s6hgVeFK.jpg", "98% Match", "Drama"),
            Movie("Mindhunter", "2019", "/xGCqUFGBIJnEGOiTbzY8KCGX0BZ.jpg", "97% Match", "Crime"),
            Movie("Narcos", "2017", "/rTmal9fDbwh5F0waol2hq35U4ah.jpg", "97% Match", "Crime"),
            Movie("Haunting of Hill House", "2018", "/6UtEIxan7UF0QFNkHaGxI6JQPG0.jpg", "95% Match", "Horror"),
            Movie("Money Heist", "2021", "/reEMJA1uzscCbkpeRJeTT2bjqUp.jpg", "98% Match", "Action"),
            Movie("Peaky Blinders", "2022", "/vUUqzWa2LnFIR3Qe4p4ljP5RFAZ.jpg", "98% Match", "Crime"),
            Movie("Orange is the New Black", "2019", "/qzA87Wf4jo1h8JMk9d9NBuj5qBs.jpg", "91% Match", "Drama"),
            Movie("House of Cards", "2018", "/hKWxWjFwnMvkWQawbhvSeFDCVMb.jpg", "90% Match", "Drama"),
            Movie("When They See Us", "2019", "/l9UrVMUL0JmXyVgDVhDzTVaJYnX.jpg", "99% Match", "Drama"),
            Movie("The Last Dance", "2020", "/vG7J2GbPAbKnBg5Y58VgGJe5EFQZ.jpg", "99% Match", "Documentary"),
            Movie("Tiger King", "2021", "/lFrmMBQ4DKJB63yfwSdmGPFe7DM.jpg", "87% Match", "Documentary"),
            Movie("The OA", "2019", "/pzIHbmZBB9cyTFkWHGXBhGSFrRF.jpg", "88% Match", "Sci-Fi")
        )),
        MovieSection("Action & Adventure", listOf(
            Movie("Red Notice", "2021", "/dBfSAULIcLBpnMzqyHK1vGm0Snf.jpg", "88% Match", "Action"),
            Movie("The Gray Man", "2022", "/jf5pOoFMFQVVFMSWPJzBLYbQGDG.jpg", "85% Match", "Action"),
            Movie("Extraction", "2020", "/qhRex189iu2srkJpUGkB1vVXhol.jpg", "90% Match", "Action"),
            Movie("Army of the Dead", "2021", "/eLT8Cu357VOwBVTitkmlDEg32Fs.jpg", "79% Match", "Action"),
            Movie("Enola Holmes 2", "2022", "/riYInlsq2kf1AnDXFLl8YbHkWqf.jpg", "93% Match", "Adventure"),
            Movie("Project Power", "2020", "/1enuT4CpQSYcPdJIFjhMGLLNEWe.jpg", "82% Match", "Action"),
            Movie("The Harder They Fall", "2021", "/xOrO0CQSM2KDuYuCm4Z7qdW5gEJ.jpg", "88% Match", "Western"),
            Movie("Gunpowder Milkshake", "2021", "/4SHIHAZHjW3vQZzv4SiMKMQVTn5.jpg", "76% Match", "Action"),
            Movie("The Mother", "2023", "/kVG8zFFYrlIkjJEzkHiWvgSex9v.jpg", "82% Match", "Action"),
            Movie("6 Underground", "2019", "/eTw4YGQMLLA2lB4Ys0yQFBGTfJo.jpg", "74% Match", "Action"),
            Movie("Interceptor", "2022", "/zItKYlFkFtYHgxF6ZGR4nFKZBGP.jpg", "70% Match", "Action"),
            Movie("Spenser Confidential", "2020", "/sWOaGbKS8DpALW3D1MkLlEjMX1B.jpg", "78% Match", "Action")
        )),
        MovieSection("Sci-Fi & Fantasy", listOf(
            Movie("Don't Look Up", "2021", "/uu4FMe7UhMdnx7I7KT5ItSRyZCE.jpg", "93% Match", "Sci-Fi"),
            Movie("Love Death + Robots", "2022", "/q2oA8KJBhnMxgjR5P7S5gLyp8UB.jpg", "97% Match", "Sci-Fi"),
            Movie("Lost in Space", "2021", "/rqbCbjB19amtOtFQbb3K2lgm2zv.jpg", "89% Match", "Sci-Fi"),
            Movie("Altered Carbon", "2020", "/tQ91hAftGSMrA2bCT5rGRQKEFN2.jpg", "86% Match", "Sci-Fi"),
            Movie("Sense8", "2018", "/7gYhtKKN0FqaFBSVAnJnJ4UUUTZ.jpg", "91% Match", "Sci-Fi"),
            Movie("Black Mirror", "2023", "/q3E7GBL0GxK4BPQKeTbGmZPJbCr.jpg", "94% Match", "Sci-Fi"),
            Movie("Dark", "2020", "/apbrbWs5M9e2ngfDFgEGJ5G7u5J.jpg", "94% Match", "Sci-Fi"),
            Movie("The Umbrella Academy", "2022", "/scZlQQYnDVlnpxFTxaIv2g0BWnL.jpg", "94% Match", "Fantasy"),
            Movie("Another Life", "2021", "/fKNEkx19Y8H5nIqL2MlTG8DeFOl.jpg", "71% Match", "Sci-Fi"),
            Movie("The Cloverfield Paradox", "2018", "/1TZp9ZCBD2b74OkLSOfDmdUzLVY.jpg", "66% Match", "Sci-Fi")
        )),
        MovieSection("Thrillers", listOf(
            Movie("Haunting of Bly Manor", "2020", "/inJjDhCjfhh3RtrEWlcfZZNTTop.jpg", "91% Match", "Horror"),
            Movie("Behind Her Eyes", "2021", "/7D3VoFkFU5DuOLbFoKNfvSTVRUo.jpg", "88% Match", "Thriller"),
            Movie("You", "2021", "/7yaEuBHdqJmGTvSd2SiSlOPSEPV.jpg", "93% Match", "Thriller"),
            Movie("Mindhunter", "2019", "/xGCqUFGBIJnEGOiTbzY8KCGX0BZ.jpg", "97% Match", "Crime"),
            Movie("Ozark", "2022", "/4EYPN5mVIhKLfxGruy7Dy41dTVn.jpg", "97% Match", "Crime"),
            Movie("The Serpent", "2021", "/mbm8k3GFhXS0Rock58umUmj4pHT.jpg", "88% Match", "Crime"),
            Movie("Making a Murderer", "2018", "/apbrbWs5M9e2ngfDFgEGJ5G7u5J.jpg", "97% Match", "Documentary"),
            Movie("Night Stalker", "2021", "/6UtEIxan7UF0QFNkHaGxI6JQPG0.jpg", "85% Match", "Crime"),
            Movie("Don't F**k with Cats", "2019", "/rTmal9fDbwh5F0waol2hq35U4ah.jpg", "93% Match", "Crime"),
            Movie("Killer Inside", "2020", "/lFrmMBQ4DKJB63yfwSdmGPFe7DM.jpg", "89% Match", "Documentary")
        )),
        MovieSection("Award-Winning Dramas", listOf(
            Movie("The Irishman", "2019", "/mbm8k3GFhXS0Rock58umUmj4pHT.jpg", "96% Match", "Drama"),
            Movie("Malcolm & Marie", "2021", "/zcKrGuvBPuuMdC2q0dCUxbMbXNI.jpg", "83% Match", "Drama"),
            Movie("Mank", "2020", "/4jgXpCHKhIzBW5xVzBrDZLOSLFo.jpg", "89% Match", "Drama"),
            Movie("The Trial of Chicago 7", "2020", "/pqCBsA5lfqxT2yCbLbSqzGbAfSK.jpg", "97% Match", "Drama"),
            Movie("Power of the Dog", "2021", "/zzRSHKSMKq39SB5n7FCdI4KfWvL.jpg", "91% Match", "Drama"),
            Movie("Roma", "2018", "/eLT8Cu357VOwBVTitkmlDEg32Fs.jpg", "97% Match", "Drama"),
            Movie("The Two Popes", "2019", "/jf5pOoFMFQVVFMSWPJzBLYbQGDG.jpg", "94% Match", "Drama"),
            Movie("The Lost Daughter", "2021", "/q2oA8KJBhnMxgjR5P7S5gLyp8UB.jpg", "88% Match", "Drama")
        )),
        MovieSection("Comedies", listOf(
            Movie("Never Have I Ever", "2023", "/oB1CKdNKzpS2O5FVBFTMa1eDcvH.jpg", "94% Match", "Comedy"),
            Movie("Ginny & Georgia", "2023", "/bfEHkdBGCFGFyfTIvPAy0BLlYoB.jpg", "88% Match", "Comedy"),
            Movie("Emily in Paris", "2022", "/AuGiPiGMYMkSosOJ9BQdYrqnVkK.jpg", "88% Match", "Comedy"),
            Movie("Sex Education", "2023", "/sNoKMslxPEJVwAV3WJHZ0t97g04.jpg", "91% Match", "Comedy"),
            Movie("Bridgerton", "2022", "/luoKpgVwi1E5nQsi7W0UuKHu2Rq.jpg", "96% Match", "Romance"),
            Movie("Dead to Me", "2021", "/jf5pOoFMFQVVFMSWPJzBLYbQGDG.jpg", "88% Match", "Comedy"),
            Movie("The Kominsky Method", "2021", "/scZlQQYnDVlnpxFTxaIv2g0BWnL.jpg", "91% Match", "Comedy"),
            Movie("Arrested Development", "2019", "/ekZobS8isE6mA53RAiGDG93hBxL.jpg", "87% Match", "Comedy"),
            Movie("Unbreakable Kimmy Schmidt", "2019", "/7yaEuBHdqJmGTvSd2SiSlOPSEPV.jpg", "86% Match", "Comedy"),
            Movie("The Ranch", "2020", "/4SHIHAZHjW3vQZzv4SiMKMQVTn5.jpg", "79% Match", "Comedy")
        )),
        MovieSection("K-Drama", listOf(
            Movie("Squid Game", "2021", "/dDlEmu3EZ0Pgg93K2SVNLCjCSvE.jpg", "99% Match", "Thriller"),
            Movie("All of Us Are Dead", "2022", "/aePKFDc5EyJXkff2MF0OSFQ0RBm.jpg", "90% Match", "Horror"),
            Movie("Hellbound", "2021", "/ypOKUuKAG1hEWtPzjbMqHhkBLlE.jpg", "83% Match", "Horror"),
            Movie("Kingdom", "2020", "/2Xhkty9zP4XoK3fQXGIcaIDmGMQ.jpg", "93% Match", "Horror"),
            Movie("The Glory", "2023", "/zcKrGuvBPuuMdC2q0dCUxbMbXNI.jpg", "97% Match", "Drama"),
            Movie("My Name", "2021", "/4jgXpCHKhIzBW5xVzBrDZLOSLFo.jpg", "91% Match", "Action"),
            Movie("Crash Landing on You", "2020", "/pqCBsA5lfqxT2yCbLbSqzGbAfSK.jpg", "97% Match", "Romance"),
            Movie("Vincenzo", "2021", "/mbm8k3GFhXS0Rock58umUmj4pHT.jpg", "95% Match", "Crime"),
            Movie("Sweet Home", "2021", "/7HW0uyH4Xl8YL9MNgQMhGJGLxQK.jpg", "88% Match", "Horror"),
            Movie("D.P.", "2021", "/uu4FMe7UhMdnx7I7KT5ItSRyZCE.jpg", "88% Match", "Drama"),
            Movie("Juvenile Justice", "2022", "/vG7J2GbPAbKnBg5Y58VgGJe5EFQZ.jpg", "91% Match", "Drama")
        )),
        MovieSection("Documentaries", listOf(
            Movie("The Last Dance", "2020", "/vG7J2GbPAbKnBg5Y58VgGJe5EFQZ.jpg", "99% Match", "Sports"),
            Movie("Tiger King", "2021", "/lFrmMBQ4DKJB63yfwSdmGPFe7DM.jpg", "87% Match", "Crime"),
            Movie("Wild Wild Country", "2018", "/q2oA8KJBhnMxgjR5P7S5gLyp8UB.jpg", "97% Match", "True Crime"),
            Movie("Seaspiracy", "2021", "/uu4FMe7UhMdnx7I7KT5ItSRyZCE.jpg", "88% Match", "Nature"),
            Movie("Icarus", "2017", "/dBfSAULIcLBpnMzqyHK1vGm0Snf.jpg", "97% Match", "Sports"),
            Movie("American Factory", "2019", "/eLT8Cu357VOwBVTitkmlDEg32Fs.jpg", "95% Match", "Social"),
            Movie("Fyre", "2019", "/qhRex189iu2srkJpUGkB1vVXhol.jpg", "95% Match", "True Crime"),
            Movie("Making a Murderer", "2018", "/apbrbWs5M9e2ngfDFgEGJ5G7u5J.jpg", "97% Match", "Crime"),
            Movie("Don't F**k with Cats", "2019", "/rTmal9fDbwh5F0waol2hq35U4ah.jpg", "93% Match", "Crime"),
            Movie("High Score", "2020", "/jf5pOoFMFQVVFMSWPJzBLYbQGDG.jpg", "92% Match", "Gaming")
        )),
        MovieSection("Popular on Netflix", listOf(
            Movie("Wednesday", "2022", "/9PFonBhy4cQy7Jz20NpMygczOkv.jpg", "98% Match", "Fantasy"),
            Movie("Glass Onion", "2022", "/vZloFAK7NmvMGKE7VkF5UHaz0I.jpg", "91% Match", "Mystery"),
            Movie("Bird Box", "2018", "/7rO4IfvR8ynDCwFHOlMFVhGeUKl.jpg", "83% Match", "Horror"),
            Movie("Knives Out", "2019", "/pThyQovXQrw2m0s9x82twj48Jq4.jpg", "97% Match", "Mystery"),
            Movie("Parasite", "2019", "/7IiTTgloJzvGI1TAYymCfbfl3vT.jpg", "99% Match", "Thriller"),
            Movie("Joker", "2019", "/udDclJoHjfjb8Ekgsd4FDteOkCU.jpg", "92% Match", "Drama"),
            Movie("Get Out", "2017", "/tFXcEccSQMf3lfhfXKSU9iRBpa3.jpg", "98% Match", "Horror"),
            Movie("Tenet", "2020", "/k68nPLbIST6NP96JmTxmZijZcpx.jpg", "82% Match", "Action"),
            Movie("Us", "2019", "/ux2maYV9D4z5LC6CeJWxHPSGhBo.jpg", "87% Match", "Horror"),
            Movie("1917", "2019", "/iZf0KyrE25z1sage4SYFLCCrMi9.jpg", "97% Match", "War")
        ))
    )
}
