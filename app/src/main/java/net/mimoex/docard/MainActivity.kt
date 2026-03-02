package net.mimoex.docard

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcF
import android.os.Bundle
import android.widget.TextView
import java.io.IOException

class MainActivity : Activity() {

  private lateinit var tv: TextView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    tv = findViewById(R.id.tv)
    handleIntent()
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent()
  }

  private fun handleIntent() {
    val intent = intent ?: return
    val action = intent.action ?: return
    if (action != NfcAdapter.ACTION_TECH_DISCOVERED && action != NfcAdapter.ACTION_TAG_DISCOVERED) return

    val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return

    try {
      val r = DoCardReader.read(tag)
      tv.text = buildString {
        appendLine("IDm: ${r.idmHex}")
        appendLine("最終利用日(推定): ${r.month}/${r.day}")
        appendLine("残高: ${r.balanceYen} 円")
      }
    } catch (e: Exception) {
      tv.text = "読み取り失敗: ${e.message}"
    }
  }
}

data class DoCardResult(
  val idmHex: String,
  val month: Int,
  val day: Int,
  val balanceYen: Int,
)

object DoCardReader {
  private val SYSTEM_CODE = byteArrayOf(0x8D.toByte(), 0xB6.toByte()) // 8DB6
  private val SERVICE_CODE_LE = byteArrayOf(0x0F.toByte(), 0x01.toByte()) // 010F

  fun read(tag: Tag): DoCardResult {
    val nfcf = NfcF.get(tag) ?: error("NfcF not supported")
    try {
      nfcf.connect()

      // Polling
      val pollingRes = nfcf.transceive(withLength(byteArrayOf(
        0x00,              // cmd = Polling
        SYSTEM_CODE[0], SYSTEM_CODE[1],
        0x00,              // request code
        0x00               // time slot
      )))
      val idm = parseIdmFromPollingResponse(pollingRes)
      val idmHex = idm.joinToString("") { "%02x".format(it) }

      // Read Without Encryption: service=010F, block=0
      val readReq = buildReadWithoutEncryption(idm, SERVICE_CODE_LE, blockNo = 0)
      val readRes = nfcf.transceive(readReq)
      val block0 = parseFirstBlock(readRes)

      val month = block0[0].toInt() and 0xFF
      val day   = block0[1].toInt() and 0xFF
      val balance = ((block0[14].toInt() and 0xFF) shl 8) or (block0[15].toInt() and 0xFF)

      return DoCardResult(idmHex, month, day, balance)
    } catch (e: IOException) {
      throw e
    } finally {
      try { nfcf.close() } catch (_: Exception) {}
    }
  }

  private fun buildReadWithoutEncryption(idm: ByteArray, serviceCodeLE: ByteArray, blockNo: Int): ByteArray {
    // [Len][0x06][IDm(8)][#services=1][ServiceCode(2)][#blocks=1][0x80][blockNo]
    val body = ByteArray(1 + 8 + 1 + 2 + 1 + 2)
    var i = 0
    body[i++] = 0x06
    System.arraycopy(idm, 0, body, i, 8); i += 8
    body[i++] = 0x01
    body[i++] = serviceCodeLE[0]
    body[i++] = serviceCodeLE[1]
    body[i++] = 0x01
    body[i++] = 0x80.toByte()
    body[i++] = (blockNo and 0xFF).toByte()
    return withLength(body)
  }

  private fun parseIdmFromPollingResponse(res: ByteArray): ByteArray {
    // [Len][0x01][IDm(8)][PMm(8)]...
    if (res.size < 18) error("Polling response too short")
    if (res[1] != 0x01.toByte()) error("Not polling response")
    return res.copyOfRange(2, 10)
  }

  private fun parseFirstBlock(res: ByteArray): ByteArray {
    // [Len][0x07][IDm(8)][Status1][Status2][#blocks][BlockData...]
    if (res.size < 13 + 16) error("Read response too short")
    if (res[1] != 0x07.toByte()) error("Not read response")
    val status1 = res[10].toInt() and 0xFF
    val status2 = res[11].toInt() and 0xFF
    if (status1 != 0x00 || status2 != 0x00) error("Read failed: s1=$status1 s2=$status2")
    val numBlocks = res[12].toInt() and 0xFF
    if (numBlocks < 1) error("No blocks")
    val start = 13
    return res.copyOfRange(start, start + 16)
  }

  private fun withLength(body: ByteArray): ByteArray {
    val len = (body.size + 1) and 0xFF
    return byteArrayOf(len.toByte()) + body
  }
}