package com.lion.rafiki.auth

import org.apache.commons.codec.binary.Hex

import java.nio.charset.Charset
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

case class PrivateKey(key: Array[Byte])

case class CryptoBits(key: PrivateKey) {
  import java.util.Base64
  def sign(message: String): String = {
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(new SecretKeySpec(key.key, "HmacSHA1"))
    Hex.encodeHexString(mac.doFinal(message.getBytes("utf-8")))
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
        if(constantTimeEquals(signature, signed)) Some(raw) else None
      }
      case _ => None
    }
  }

  def constantTimeEquals(a: String, b: String): Boolean = {
    var equal = 0
    for (i <- 0 until (a.length min b.length)) {
      equal |= a(i) ^ b(i)
    }
    if (a.length != b.length) {
      false
    } else {
      equal == 0
    }
  }
}
