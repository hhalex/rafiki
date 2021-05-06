package com.lion.rafiki.auth

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

case class PrivateKey(key: Array[Byte])

case class CryptoBits(key: PrivateKey) {
  private def convertBytesToHex(bytes: Seq[Byte]): String = {
    val sb = new StringBuilder
    for b <- bytes do {
      sb.append(String.format("%02x", Byte.box(b)))
    }
    sb.toString
  }

  import java.util.Base64
  private def sign(message: String): String = {
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(key.key, "HmacSHA1"))
    convertBytesToHex(mac.doFinal(message.getBytes("utf-8")).toIndexedSeq)
  }

  def signToken(token: String, nonce: String): String = {
    val joined = nonce + "-" + token
    val mesg = sign(joined) + "-" + joined
    Base64.getEncoder.encodeToString(mesg.getBytes)
  }

  def validateSignedToken(b64Token: String): Option[String] = {
    val bytes = Base64.getDecoder.decode(b64Token)
    new String(bytes).split("-", 3) match {
      case Array(signature, nonce, raw) => {
        val signed = sign(nonce + "-" + raw)
        if constantTimeEquals(signature, signed) then Some(raw) else None
      }
      case _ => None
    }
  }

  private def constantTimeEquals(a: String, b: String): Boolean = {
    var equal = 0
    for i <- 0 until (a.length min b.length) do {
      equal |= a(i) ^ b(i)
    }
    if a.length != b.length then
      false
    else
      equal == 0
  }
}
