export interface ReviewArtifact { attachmentId: string; checksum: string }
export interface ReviewCandidate { proposalId: string; revisionId: string; proposal: ReviewArtifact; ontology?: ReviewArtifact }
export interface ReviewProposal { document: string; ontologySource?: string; documentChecksum: string;
  ontologyChecksum?: string; proposalId?: string; revisionId?: string; schemaCheck: { status: string } }
export interface ReviewContribution { id: string; owner: string; text: string; submitted: boolean; updatedAt: string; proposal?: ReviewProposal }
export interface ReviewRound { id: string; audience: "REVIEWER" | "COMMUNITY"; closedAt?: string; contributions: ReviewContribution[] }
export interface ReviewCycle { id: string; sourceStateId: string; candidate: ReviewCandidate; closedAt?: string; rounds: ReviewRound[] }
export interface ReviewView { revision: number; cycles: ReviewCycle[]; canManage: boolean; identity: string; contributableRoundIds: string[] }
