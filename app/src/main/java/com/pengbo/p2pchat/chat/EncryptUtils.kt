package com.pengbo.p2pchat.chat

import io.libp2p.core.PeerId
import io.libp2p.crypto.keys.Ed25519PrivateKey
import io.libp2p.core.crypto.PrivKey
import io.libp2p.crypto.keys.Ed25519PublicKey
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import java.security.MessageDigest

class EncryptUtils {
    companion object {
        fun generatePrivateKeyFromNumber(number: String): PrivKey {
            try {
                val seed = MessageDigest.getInstance("SHA-256")
                    .digest(number.toByteArray(Charsets.UTF_8))
                    .copyOf(32)
                val privateKeyParams = Ed25519PrivateKeyParameters(seed, 0)
                return Ed25519PrivateKey(privateKeyParams)
            } catch (e: Exception) {
                e.printStackTrace()
                throw IllegalArgumentException(
                    "generate key from number [$number] failed: ${e.message}",
                    e
                )
            }
        }

        fun getPeerIdFromPhone(phone: String): PeerId {
            try {
                val seed = MessageDigest.getInstance("SHA-256")
                    .digest(phone.toByteArray(Charsets.UTF_8))
                    .copyOf(32)
                val pubKeyParams = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey()
                return PeerId.fromPubKey(Ed25519PublicKey(pubKeyParams))
            } catch (e: Exception) {
                e.printStackTrace()
                throw IllegalArgumentException(
                    "get PeerId from number [$phone] failed: ${e.message}",
                    e
                )
            }
        }
    }

}