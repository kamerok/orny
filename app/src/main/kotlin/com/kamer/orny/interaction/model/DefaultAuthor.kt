package com.kamer.orny.interaction.model

import com.kamer.orny.data.domain.model.Author


data class DefaultAuthor(
        val selectedAuthor: Author = Author.EMPTY_AUTHOR,
        val authors: List<Author> = emptyList()
)