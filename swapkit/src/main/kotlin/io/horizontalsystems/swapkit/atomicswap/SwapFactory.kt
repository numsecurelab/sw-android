package io.horizontalsystems.swapkit.atomicswap

import java.security.MessageDigest
import java.util.*

class SwapFactory(private val db: SwapDatabase) {
    private val swapBlockchainCreators = mutableMapOf<String, ISwapBlockchainCreator>()

    fun registerSwapBlockchainCreator(coinCode: String, creator: ISwapBlockchainCreator) {
        swapBlockchainCreators[coinCode] = creator
    }

    fun createBlockchain(coinCode: String): ISwapBlockchain {
        return swapBlockchainCreators[coinCode]?.create() ?: throw AtomicSwapNotSupported(coinCode)
    }

    fun createAtomicSwapResponder(swap: Swap): SwapResponder {
        val initiatorBlockchain = createBlockchain(swap.initiatorCoinCode)
        val responderBlockchain = createBlockchain(swap.responderCoinCode)

        return SwapResponder(initiatorBlockchain, responderBlockchain, swap, db)
    }

    fun createAtomicSwapInitiator(swap: Swap): SwapInitiator {
        val initiatorBlockchain = createBlockchain(swap.initiatorCoinCode)
        val responderBlockchain = createBlockchain(swap.responderCoinCode)

        return SwapInitiator(initiatorBlockchain, responderBlockchain, swap, db)
    }

    fun createSwap(initiatorCoinCode: String, responderCoinCode: String, rate: Double, amount: Double): Swap {
        val initiatorBlockchain = createBlockchain(initiatorCoinCode)
        val responderBlockchain = createBlockchain(responderCoinCode)

        val initiatorRedeemPublicKey = responderBlockchain.getRedeemPublicKey()
        val initiatorRefundPublicKey = initiatorBlockchain.getRefundPublicKey()

        val id = UUID.randomUUID().toString()
        val secret = sha256("supersecret".toByteArray())

        val swap = Swap().apply {
            this.id = id
            this.initiator = true
            this.state = Swap.State.REQUESTED

            this.initiatorCoinCode = initiatorCoinCode
            this.responderCoinCode = responderCoinCode
            this.rate = rate
            this.initiatorAmount = "$amount"

            this.initiatorRedeemPKH = initiatorRedeemPublicKey.publicKeyHash
            this.initiatorRedeemPKId = initiatorRedeemPublicKey.publicKeyId
            this.initiatorRefundPKH = initiatorRefundPublicKey.publicKeyHash
            this.initiatorRefundPKId = initiatorRefundPublicKey.publicKeyId

            this.secret = secret
            this.secretHash = sha256(secret)
        }

        db.swapDao.save(swap)

        return swap
    }

    fun createSwapAsResponder(id: String, initiatorCoinCode: String, responderCoinCode: String, rate: Double, amount: Double, initiatorRefundPKH: ByteArray, initiatorRedeemPKH: ByteArray, secretHash: ByteArray): Swap {
        val initiatorBlockchain = createBlockchain(initiatorCoinCode)
        val responderBlockchain = createBlockchain(responderCoinCode)

        val redeemPublicKey = initiatorBlockchain.getRedeemPublicKey()
        val refundPublicKey = responderBlockchain.getRefundPublicKey()

        val swap = Swap().apply {
            this.id = id
            this.initiator = false
            this.state = Swap.State.RESPONDED

            this.initiatorCoinCode = initiatorCoinCode
            this.responderCoinCode = responderCoinCode
            this.rate = rate
            this.initiatorAmount = "$amount"

            this.initiatorRedeemPKH = initiatorRedeemPKH
            this.initiatorRefundPKH = initiatorRefundPKH

            this.secretHash = sha256(secret)

            responderRedeemPKH = redeemPublicKey.publicKeyHash
            responderRedeemPKId = redeemPublicKey.publicKeyId
            responderRefundPKH = refundPublicKey.publicKeyHash
            responderRefundPKId = refundPublicKey.publicKeyId

            responderRefundTime = 1563000000 // 07/13/2019 @ 6:40am (UTC)
            initiatorRefundTime = 1563100000 // 07/14/2019 @ 10:26am (UTC)
        }

        db.swapDao.save(swap)

        return swap
    }

    fun retrieveSwapForResponse(id: String, responderRedeemPKH: ByteArray, responderRefundPKH: ByteArray, responderRefundTime: Long, initiatorRefundTime: Long) : Swap {
        val swapFromDB = db.swapDao.load(id)

        swapFromDB.apply {
            this.state = Swap.State.RESPONDED

            this.responderRedeemPKH = responderRedeemPKH
            this.responderRefundPKH = responderRefundPKH

            this.responderRefundTime = responderRefundTime
            this.initiatorRefundTime = initiatorRefundTime
        }

        db.swapDao.save(swapFromDB)

        return swapFromDB
    }

    private fun sha256(input: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input)
    }

}

interface ISwapBlockchainCreator {
    fun create(): ISwapBlockchain
}

class AtomicSwapNotSupported(coinCode: String) : Exception("Atomic swap is not supported for $coinCode")
