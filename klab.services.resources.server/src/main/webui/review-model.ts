export interface ReviewArtifact { attachmentId: string; checksum: string }
export interface ReviewCandidate { proposalId: string; revisionId: string; proposal: ReviewArtifact; ontology?: ReviewArtifact }
export interface ReviewProposal { document: string; ontologySource?: string; documentChecksum: string;
  ontologyChecksum?: string; proposalId?: string; revisionId?: string; schemaCheck: { status: string } }
export interface ReviewContribution { id: string; owner: string; text: string; submitted: boolean; updatedAt: string; proposal?: ReviewProposal }
export interface ReviewRound { id: string; stageId?: string; audience: "REVIEWER" | "COMMUNITY"; closedAt?: string; contributions: ReviewContribution[] }
export interface ReviewCycle { id: string; sourceStateId: string; candidate: ReviewCandidate; closedAt?: string; rounds: ReviewRound[] }
export interface ReviewView { revision: number; cycles: ReviewCycle[]; canManage: boolean; identity: string; contributableRoundIds: string[]; phase?: "BOOTSTRAP" | "REVIEW" | "COALESCING" | "TERMINATED"; editableStageId?: string; stages?: ReviewStage[]; canOpenCycle?: boolean; canCoalesce?: boolean; canTerminate?: boolean; canOpenRound?: boolean }

export interface ReviewStage { id: string; schemaId: string; cycleId?: string; roundId?: string; open: boolean; canEdit: boolean; canContribute: boolean }
