package com.sksamuel.hoplite.watch

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.PropertySource
import com.sksamuel.hoplite.watch.watchers.FileWatcher
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempfile
import io.kotest.framework.concurrency.eventually
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.delay


class TestWatcher: Watchable {
  var cb: (() -> Unit)? = null
  override fun watch(callback: () -> Unit, errorHandler: (Throwable) -> Unit) {
    cb = callback
  }

  fun update() {
    cb?.invoke()
  }
}

data class TestConfig(val foo: String)

@ExperimentalKotest
class WatcherTest : FunSpec({
  test("will call the provided error handler if reloadConfig throws") {
    val configLoader = ConfigLoader.Builder()
      .addSource(PropertySource.resource("does-not-exist.yml"))

    val watcher = TestWatcher()
    var error: Throwable? = null
    ReloadableConfig(configLoader.build(), TestConfig::class)
      .addWatcher(watcher)
      .addErrorHandler { error = it }

    watcher.update()

    error shouldNotBe null
    error?.message shouldContain "Could not find config file does-not-exist.yml"
  }

  test("will reload the config when the watchable triggers an update") {
    val map = mutableMapOf("foo" to "bar")
    val configLoader = ConfigLoader.Builder()
      .addSource(PropertySource.map(map))

    val watcher = TestWatcher()
    val reloadableConfig = ReloadableConfig(configLoader.build(), TestConfig::class)
      .addWatcher(watcher)

    val config = reloadableConfig.getLatest()
    config?.foo shouldBe "bar"

    map["foo"] = "baz"
    watcher.update()

    val reloadedConfig = reloadableConfig.getLatest()
    reloadedConfig shouldNotBe null
    reloadedConfig?.foo shouldBe "baz"
  }

  test("FileWatcher will reload if a file in the specified directory changes") {
    val tmpFile = tempfile("file", ".json")
    tmpFile.writeText("""{"foo": "bar"}""")

    val configLoader = ConfigLoader.Builder()
      .addSource(PropertySource.file(tmpFile))
      .build()

    val reloadableConfig = ReloadableConfig(configLoader, TestConfig::class)
      .addWatcher(FileWatcher(tmpFile.parent))

    val config = reloadableConfig.getLatest()
    config?.foo shouldBe "bar"

    delay(1000)
    tmpFile.writeText("""{"foo": "baz"}""")

    eventually(10000) {
      val reloadedConfig = reloadableConfig.getLatest()
      reloadedConfig shouldNotBe null
      reloadedConfig?.foo shouldBe "baz"
    }
  }
})
