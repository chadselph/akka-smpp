package akkasmpp.protocol

/**
 * Error in the SMPP protocol
 */
class SmppProtocolError(m: String) extends Exception(m)
