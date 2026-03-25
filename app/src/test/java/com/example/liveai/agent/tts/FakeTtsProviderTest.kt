package com.example.liveai.agent.tts

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)

class FakeTtsProviderTest {

    @Test
    fun `records spoken text`() = runTest {
        val tts = FakeTtsProvider()
        tts.speak("Hello")
        tts.speak("World")

        assertEquals(listOf("Hello", "World"), tts.spokenTexts)
    }

    @Test
    fun `isSpeaking is false when idle`() {
        val tts = FakeTtsProvider()
        assertFalse(tts.isSpeaking)
    }

    @Test
    fun `isSpeaking is true during speak with delay`() = runTest {
        val tts = FakeTtsProvider(speakDelayMs = 1000L)

        val job = launch { tts.speak("slow") }
        advanceTimeBy(500)

        assertTrue(tts.isSpeaking)

        advanceTimeBy(600)
        job.join()

        assertFalse(tts.isSpeaking)
    }

    @Test
    fun `isSpeaking is false after speak completes`() = runTest {
        val tts = FakeTtsProvider()
        tts.speak("done")
        assertFalse(tts.isSpeaking)
    }

    @Test
    fun `stop sets flag`() {
        val tts = FakeTtsProvider()
        assertFalse(tts.stopCalled)

        tts.stop()

        assertTrue(tts.stopCalled)
        assertFalse(tts.isSpeaking)
    }

    @Test
    fun `stop cancels isSpeaking`() = runTest {
        val tts = FakeTtsProvider(speakDelayMs = 5000L)

        val job = launch { tts.speak("long") }
        advanceTimeBy(100)
        assertTrue(tts.isSpeaking)

        tts.stop()
        assertFalse(tts.isSpeaking)

        job.cancel()
    }

    @Test
    fun `reset clears all tracking`() = runTest {
        val tts = FakeTtsProvider()
        tts.speak("hello")
        tts.stop()

        assertEquals(1, tts.spokenTexts.size)
        assertTrue(tts.stopCalled)

        tts.reset()

        assertTrue(tts.spokenTexts.isEmpty())
        assertFalse(tts.stopCalled)
        assertFalse(tts.isSpeaking)
    }

    @Test
    fun `speak with zero delay completes immediately`() = runTest {
        val tts = FakeTtsProvider(speakDelayMs = 0L)
        tts.speak("instant")
        assertEquals(listOf("instant"), tts.spokenTexts)
        assertFalse(tts.isSpeaking)
    }

    @Test
    fun `multiple speaks recorded in order`() = runTest {
        val tts = FakeTtsProvider()
        tts.speak("first")
        tts.speak("second")
        tts.speak("third")

        assertEquals(listOf("first", "second", "third"), tts.spokenTexts)
    }
}
