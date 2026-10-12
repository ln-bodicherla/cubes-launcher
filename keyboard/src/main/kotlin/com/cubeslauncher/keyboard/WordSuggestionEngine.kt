package com.cubeslauncher.keyboard

class WordSuggestionEngine {

    private val learnedWords = mutableMapOf<String, Int>() // word -> frequency

    fun getSuggestions(prefix: String, limit: Int = 3): List<String> {
        if (prefix.isBlank()) return emptyList()
        val lower = prefix.lowercase()

        val results = mutableListOf<Pair<String, Int>>()

        // 1. Prefix matches (highest priority)
        for ((word, freq) in learnedWords) {
            if (word.startsWith(lower) && word != lower) {
                results.add(word to (freq + 2000))
            }
        }
        for (word in DICTIONARY) {
            if (word.startsWith(lower) && word != lower && results.none { it.first == word }) {
                results.add(word to 500)
            }
        }

        // 2. Fuzzy corrections: edit distance + QWERTY neighbor awareness
        // Only if the typed word is long enough to meaningfully correct
        if (lower.length >= 3) {
            val maxDist = if (lower.length <= 4) 1 else 2
            for ((word, freq) in learnedWords) {
                if (results.any { it.first == word }) continue
                if (Math.abs(word.length - lower.length) > maxDist) continue
                val dist = keyboardEditDistance(lower, word, maxDist)
                if (dist in 1..maxDist) {
                    results.add(word to (freq + 1000 - dist * 200))
                }
            }
            for (word in DICTIONARY) {
                if (results.any { it.first == word }) continue
                if (Math.abs(word.length - lower.length) > maxDist) continue
                val dist = keyboardEditDistance(lower, word, maxDist)
                if (dist in 1..maxDist) {
                    results.add(word to (300 - dist * 100))
                }
            }
        }

        // 3. Exact match of typed word (include as-is if it's a real word)
        if (DICTIONARY_SET.contains(lower) || learnedWords.containsKey(lower)) {
            results.add(lower to 3000)
        }

        results.sortByDescending { it.second }
        return results.take(limit).map { it.first }
    }

    /**
     * Keyboard-aware edit distance: substitutions of QWERTY-adjacent keys
     * cost 0.5 (counted as 1 but with a score bonus), normal substitutions cost 1.
     * Returns Int edit distance, capped at maxDist+1 for early termination.
     */
    private fun keyboardEditDistance(a: String, b: String, maxDist: Int): Int {
        val m = a.length
        val n = b.length
        if (Math.abs(m - n) > maxDist) return maxDist + 1

        // Optimized single-row DP
        val prev = IntArray(n + 1) { it }
        val curr = IntArray(n + 1)

        for (i in 1..m) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0
                           else if (KEY_NEIGHBORS[a[i - 1]]?.contains(b[j - 1]) == true) 1
                           else 1
                curr[j] = minOf(
                    prev[j] + 1,      // deletion
                    curr[j - 1] + 1,  // insertion
                    prev[j - 1] + cost // substitution
                )
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            // Early termination if entire row exceeds maxDist
            if (rowMin > maxDist) return maxDist + 1
            System.arraycopy(curr, 0, prev, 0, n + 1)
        }
        return prev[n]
    }

    fun learn(word: String) {
        if (word.length < 2) return
        val lower = word.lowercase()
        learnedWords[lower] = (learnedWords[lower] ?: 0) + 1
    }

    fun getSwipeSuggestions(swipedKeys: List<Key>, limit: Int = 5): List<String> {
        if (swipedKeys.size < 2) return emptyList()

        val swipedChars = swipedKeys.mapNotNull { it.outputChar?.lowercaseChar() }
        if (swipedChars.size < 2) return emptyList()

        val firstChar = swipedChars.first()
        val lastChar = swipedChars.last()

        val results = mutableListOf<Pair<String, Int>>()

        // Score learned words
        for ((word, freq) in learnedWords) {
            if (word.length < 2) continue
            if (word.first() != firstChar && !KEY_NEIGHBORS[firstChar].orEmpty().contains(word.first())) continue
            if (word.last() != lastChar && !KEY_NEIGHBORS[lastChar].orEmpty().contains(word.last())) continue
            val score = scoreSwipeMatch(word, swipedChars, freq)
            if (score > 0) results.add(word to score)
        }

        // Score dictionary words
        for (word in DICTIONARY) {
            if (word.length < 2) continue
            if (results.any { it.first == word }) continue
            if (word.first() != firstChar && !KEY_NEIGHBORS[firstChar].orEmpty().contains(word.first())) continue
            if (word.last() != lastChar && !KEY_NEIGHBORS[lastChar].orEmpty().contains(word.last())) continue
            val score = scoreSwipeMatch(word, swipedChars, 0)
            if (score > 0) results.add(word to score)
        }

        results.sortByDescending { it.second }
        return results.take(limit).map { it.first }
    }

    private fun scoreSwipeMatch(word: String, swipedChars: List<Char>, learnedFreq: Int): Int {
        // Try strict subsequence first
        if (isSubsequence(word, swipedChars)) {
            val lengthPenalty = 5 * Math.abs(word.length - swipedChars.size)
            return 100 - lengthPenalty + (10 * learnedFreq)
        }

        // Fuzzy match: allow neighbor-key substitutions
        if (isFuzzySubsequence(word, swipedChars)) {
            val lengthPenalty = 5 * Math.abs(word.length - swipedChars.size)
            return 80 - lengthPenalty + (10 * learnedFreq) // Lower base for fuzzy
        }

        return 0
    }

    private fun isSubsequence(word: String, swipedChars: List<Char>): Boolean {
        var si = 0
        for (wi in word.indices) {
            // Consecutive repeated letter in word — same physical key, free match
            if (wi > 0 && word[wi] == word[wi - 1]) continue

            var found = false
            while (si < swipedChars.size) {
                if (swipedChars[si] == word[wi]) {
                    si++
                    found = true
                    break
                }
                si++
            }
            if (!found) return false
        }
        return true
    }

    private fun isFuzzySubsequence(word: String, swipedChars: List<Char>): Boolean {
        var si = 0
        for (wi in word.indices) {
            if (wi > 0 && word[wi] == word[wi - 1]) continue

            val wc = word[wi]
            var found = false
            while (si < swipedChars.size) {
                val sc = swipedChars[si]
                if (wc == sc || KEY_NEIGHBORS[sc].orEmpty().contains(wc)) {
                    si++
                    found = true
                    break
                }
                si++
            }
            if (!found) return false
        }
        return true
    }

    companion object {
        val KEY_NEIGHBORS: Map<Char, Set<Char>> = mapOf(
            'q' to setOf('w', 'a'),
            'w' to setOf('q', 'e', 'a', 's'),
            'e' to setOf('w', 'r', 's', 'd'),
            'r' to setOf('e', 't', 'd', 'f'),
            't' to setOf('r', 'y', 'f', 'g'),
            'y' to setOf('t', 'u', 'g', 'h'),
            'u' to setOf('y', 'i', 'h', 'j'),
            'i' to setOf('u', 'o', 'j', 'k'),
            'o' to setOf('i', 'p', 'k', 'l'),
            'p' to setOf('o', 'l'),
            'a' to setOf('q', 'w', 's', 'z'),
            's' to setOf('a', 'w', 'e', 'd', 'z', 'x'),
            'd' to setOf('s', 'e', 'r', 'f', 'x', 'c'),
            'f' to setOf('d', 'r', 't', 'g', 'c', 'v'),
            'g' to setOf('f', 't', 'y', 'h', 'v', 'b'),
            'h' to setOf('g', 'y', 'u', 'j', 'b', 'n'),
            'j' to setOf('h', 'u', 'i', 'k', 'n', 'm'),
            'k' to setOf('j', 'i', 'o', 'l', 'm'),
            'l' to setOf('k', 'o', 'p'),
            'z' to setOf('a', 's', 'x'),
            'x' to setOf('z', 's', 'd', 'c'),
            'c' to setOf('x', 'd', 'f', 'v'),
            'v' to setOf('c', 'f', 'g', 'b'),
            'b' to setOf('v', 'g', 'h', 'n'),
            'n' to setOf('b', 'h', 'j', 'm'),
            'm' to setOf('n', 'j', 'k')
        )

        private val DICTIONARY_SET: Set<String> by lazy { DICTIONARY.toSet() }

        private val DICTIONARY = listOf(
            // Articles, pronouns, prepositions
            "the", "a", "an", "i", "you", "he", "she", "it", "we", "they",
            "me", "him", "her", "us", "them", "my", "your", "his", "its",
            "our", "their", "this", "that", "these", "those", "who", "what",
            "which", "where", "when", "how", "why", "in", "on", "at", "to",
            "for", "with", "from", "by", "about", "into", "through", "during",
            "before", "after", "above", "below", "between", "under", "over",

            // Common verbs
            "is", "am", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "having", "do", "does", "did", "doing",
            "will", "would", "shall", "should", "can", "could", "may", "might",
            "must", "go", "going", "gone", "went", "get", "getting", "got",
            "make", "making", "made", "know", "knowing", "knew", "known",
            "think", "thinking", "thought", "take", "taking", "took", "taken",
            "see", "seeing", "saw", "seen", "come", "coming", "came",
            "want", "wanting", "wanted", "look", "looking", "looked",
            "use", "using", "used", "find", "finding", "found",
            "give", "giving", "gave", "given", "tell", "telling", "told",
            "work", "working", "worked", "call", "calling", "called",
            "try", "trying", "tried", "ask", "asking", "asked",
            "need", "needing", "needed", "feel", "feeling", "felt",
            "become", "becoming", "became", "leave", "leaving", "left",
            "put", "putting", "mean", "meaning", "meant",
            "keep", "keeping", "kept", "let", "letting",
            "begin", "beginning", "began", "begun",
            "seem", "seeming", "seemed", "help", "helping", "helped",
            "show", "showing", "showed", "shown",
            "hear", "hearing", "heard", "play", "playing", "played",
            "run", "running", "ran", "move", "moving", "moved",
            "live", "living", "lived", "believe", "believing", "believed",
            "bring", "bringing", "brought", "happen", "happening", "happened",
            "write", "writing", "wrote", "written",
            "send", "sending", "sent", "expect", "expecting", "expected",
            "build", "building", "built", "stay", "staying", "stayed",
            "read", "reading", "open", "opening", "opened",
            "walk", "walking", "walked", "win", "winning", "won",
            "remember", "remembering", "remembered",
            "love", "loving", "loved", "like", "liking", "liked",
            "watch", "watching", "watched", "follow", "following", "followed",
            "stop", "stopping", "stopped", "create", "creating", "created",
            "speak", "speaking", "spoke", "spoken",
            "learn", "learning", "learned", "change", "changing", "changed",
            "understand", "understanding", "understood",
            "buy", "buying", "bought", "wait", "waiting", "waited",
            "pay", "paying", "paid", "meet", "meeting", "met",
            "eat", "eating", "ate", "eaten",
            "drink", "drinking", "drank", "drunk",
            "sleep", "sleeping", "slept", "drive", "driving", "drove",
            "talk", "talking", "talked", "start", "starting", "started",
            "finish", "finishing", "finished",

            // Common nouns
            "time", "year", "years", "people", "way", "day", "days",
            "man", "woman", "child", "children", "world", "life",
            "hand", "part", "place", "case", "week", "weeks",
            "company", "system", "program", "question", "work",
            "government", "number", "night", "point", "home",
            "water", "room", "mother", "father", "area", "money",
            "story", "fact", "month", "months", "lot", "right",
            "study", "book", "eye", "eyes", "job", "word", "words",
            "business", "issue", "side", "kind", "head", "house",
            "service", "friend", "friends", "power", "hour", "hours",
            "game", "games", "line", "end", "member", "members",
            "family", "city", "school", "name", "group", "country",
            "problem", "party", "state", "thing", "things",
            "student", "students", "food", "car", "phone",
            "email", "message", "messages", "text", "photo", "photos",
            "video", "videos", "app", "internet", "computer",
            "music", "movie", "movies", "morning", "evening",
            "today", "tomorrow", "yesterday", "weekend",
            "brother", "sister", "baby", "dog", "cat",

            // Common adjectives
            "good", "new", "first", "last", "long", "great", "little",
            "own", "other", "old", "right", "big", "high", "different",
            "small", "large", "next", "early", "young", "important",
            "few", "public", "bad", "same", "able", "free", "real",
            "sure", "best", "better", "happy", "nice", "beautiful",
            "funny", "cool", "awesome", "amazing", "wonderful",
            "terrible", "horrible", "perfect", "sorry", "busy",
            "late", "ready", "wrong", "true", "false",

            // Common adverbs
            "not", "also", "very", "often", "however", "too", "usually",
            "really", "already", "always", "sometimes", "never",
            "just", "only", "still", "even", "now", "then", "here",
            "there", "again", "together", "maybe", "perhaps",
            "probably", "certainly", "actually", "exactly",
            "definitely", "absolutely", "basically", "honestly",
            "seriously", "literally", "obviously", "apparently",

            // Social / conversational
            "hello", "hi", "hey", "thanks", "thank", "please",
            "sorry", "yes", "no", "okay", "ok", "sure", "right",
            "well", "yeah", "bye", "goodbye", "goodnight",
            "congratulations", "welcome", "awesome", "great",
            "sounds", "lol", "haha", "omg", "btw",

            // Technology
            "app", "phone", "email", "message", "text", "photo",
            "video", "internet", "computer", "music", "download",
            "upload", "search", "password", "account", "website",
            "online", "social", "media", "battery", "screen",
            "camera", "wifi", "bluetooth", "update", "install",
            "delete", "share", "post", "comment", "notification",
            "setting", "settings", "profile",

            // Conjunctions / misc
            "and", "but", "or", "if", "because", "so", "while",
            "although", "though", "since", "until", "unless",
            "than", "both", "either", "neither", "whether",
            "everything", "everyone", "something", "someone",
            "anything", "anyone", "nothing", "nobody",
            "much", "many", "more", "most", "some", "any",
            "enough", "all", "every", "each", "another"
        ).sorted()
    }
}
