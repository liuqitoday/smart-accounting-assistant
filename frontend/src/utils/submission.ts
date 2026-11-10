import type { SaveTransactionRequest } from '@/types'

export interface SubmissionIdentity {
  fingerprint: string
  key: string
}

/** Keep the same retry key until the user changes the submitted values. */
export function identifySubmission(payload: SaveTransactionRequest, identity: SubmissionIdentity): SaveTransactionRequest {
  const { clientRequestId: _ignored, ...values } = payload
  const fingerprint = JSON.stringify({ ...values, tagIds: [...(values.tagIds || [])].sort((a, b) => a - b) })
  if (identity.fingerprint !== fingerprint || !identity.key) {
    identity.fingerprint = fingerprint
    identity.key = crypto.randomUUID()
  }
  return { ...values, clientRequestId: identity.key }
}
