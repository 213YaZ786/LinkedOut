package com.linkedout.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PostIdTest {

    @Test
    fun `reads the id out of a post address, with or without words`() {
        assertEquals("7509586663443140608", PostId.normalize("https://fr.linkedin.com/posts/someone_encgm-activity-7509586663443140608-dqfR"))
        assertEquals("7509376347606183937", PostId.normalize("https://www.linkedin.com/posts/someone-8096ab25_activity-7509376347606183937-34kv"))
        assertEquals("7510452623263707136", PostId.normalize("urn:li:ugcPost:7510452623263707136"))
    }
}
