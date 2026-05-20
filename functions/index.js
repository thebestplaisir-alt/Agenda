const {onDocumentCreated, onDocumentDeleted, onDocumentUpdated} = require("firebase-functions/v2/firestore");
const {onSchedule} = require("firebase-functions/v2/scheduler");
const admin = require("firebase-admin");
const sgMail = require('@sendgrid/mail');

admin.initializeApp();

// CONFIGURATION SENDGRID
const SENDGRID_API_KEY = "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";
const SENDER_EMAIL = "agenda.padel.app@gmail.com";

sgMail.setApiKey(SENDGRID_API_KEY);

function calculateMaxConcurrent(availabilities) {
    const events = [];
    availabilities.forEach(a => {
        const count = a.isDuo ? 2 : 1;
        events.push({ time: a.startTimeString, type: count });
        events.push({ time: a.endTimeString, type: -count });
    });
    events.sort((a, b) => (a.time !== b.time ? a.time.localeCompare(b.time) : a.type - b.type));

    let maxConcurrent = 0;
    let current = 0;
    for (let e of events) {
        current += e.type;
        if (current > maxConcurrent) maxConcurrent = current;
    }
    return maxConcurrent;
}

// 1. MAIL DE BIENVENUE
exports.onProfileCreated = onDocumentCreated("profiles/{userId}", async (event) => {
    const profile = event.data.data();
    if (!profile) return null;
    try {
        const userAuth = await admin.auth().getUser(event.params.userId);
        const email = userAuth.email;
        const msg = {
            to: email,
            from: { email: SENDER_EMAIL, name: "Agenda Padel" },
            subject: 'Bienvenue sur Agenda Padel ! 🎾',
            html: `<div style="font-family: sans-serif; max-width: 600px; margin: auto; padding: 20px; border: 1px solid #eee; border-radius: 10px;">
                    <h1 style="color: #1A1C1E; text-align: center;">Bienvenue ${profile.displayName} !</h1>
                    <p>Ton code d'invitation personnel : <strong>${profile.inviteCode}</strong></p>
                </div>`,
        };
        await sgMail.send(msg);
    } catch (error) { console.error(error); }
    return null;
});

// 2. MAIL PREMIUM
exports.onPremiumPurchased = onDocumentUpdated("profiles/{userId}", async (event) => {
    const newData = event.data.after.data();
    const oldData = event.data.before.data();
    if (newData && oldData && !oldData.premium && newData.premium === true) {
        try {
            const userAuth = await admin.auth().getUser(event.params.userId);
            const email = userAuth.email;
            const msg = {
                to: email,
                from: { email: SENDER_EMAIL, name: "Agenda Padel" },
                subject: 'Félicitations ! Vous êtes maintenant PREMIUM 🌟',
                html: `<h1>Merci pour votre soutien !</h1>`,
            };
            await sgMail.send(msg);
        } catch (error) { console.error(error); }
    }
    return null;
});

// 3. NOTIFICATION CRÉATION SÉANCE (FILTRAGE AUTO-NOTIF)
exports.onAvailabilityCreated = onDocumentCreated("creneaux/{id}", async (event) => {
    const newData = event.data.data();
    if (!newData) return null;
    const { dateString, personName, groupId, userId } = newData;
    try {
        const snapshot = await admin.firestore().collection("creneaux")
            .where("dateString", "==", dateString)
            .where("groupId", "==", groupId || "")
            .get();
        const docs = snapshot.docs.map(doc => doc.data());
        const maxConcurrent = calculateMaxConcurrent(docs);
        const groupTopic = `group_${groupId}`;

        // On utilise UNIQUEMENT 'data' pour forcer le passage par onMessageReceived dans l'app
        // et permettre le filtrage du senderId
        await admin.messaging().send({
            data: {
                title: "🎾 Nouvelle dispo !",
                body: `${personName} est disponible le ${dateString}.`,
                senderId: String(userId), // On s'assure que c'est une string
                type: "NEW_AVAILABILITY",
                date: String(dateString)
            },
            topic: groupTopic
        });

        if (maxConcurrent === 4) {
            const topicMatch = `match_${groupId || "default"}_${dateString}`.replace(/[:\-]/g, "_");
            await admin.messaging().send({
                data: {
                    title: "🎾 MATCH CONFIRMÉ !",
                    body: `On est 4 le ${dateString} !`,
                    senderId: String(userId),
                    type: "MATCH_CONFIRMED",
                    date: String(dateString)
                },
                topic: topicMatch
            });
        }
    } catch (err) { console.error(err); }
    return null;
});

// 4. NOTIFICATION QUAND LE MATCH DEVIENT COMPLET
exports.onAvailabilityUpdated = onDocumentUpdated("creneaux/{id}", async (event) => {
    const before = event.data.before.data();
    const after = event.data.after.data();
    if (!before || !after) return null;

    const { dateString, groupId, userId, participantIds, isDuo } = after;
    
    try {
        const wasFull = (isDuo && before.participantIds?.length >= 2) || (!isDuo && before.participantIds?.length >= 4);
        const isFull = (isDuo && after.participantIds?.length >= 2) || (!isDuo && after.participantIds?.length >= 4);

        // Si ce n'était pas complet avant et ça le devient maintenant
        if (!wasFull && isFull) {
            const bodyText = isDuo 
                ? `Ton partenaire est trouvé pour le ${dateString} !` 
                : `On est 4 le ${dateString} !`;
            
            const titleText = isDuo
                ? "🎾 DUO CONFIRMÉ !"
                : "🎾 MATCH CONFIRMÉ !";

            // Notif au groupe
            const groupTopic = `group_${groupId}`;
            await admin.messaging().send({
                data: {
                    title: titleText,
                    body: bodyText,
                    senderId: String(userId),
                    type: "MATCH_CONFIRMED",
                    date: String(dateString)
                },
                topic: groupTopic
            });

            // Notif au joueur qui a créé la dispo
            const creatorTopic = `user_${userId}`;
            await admin.messaging().send({
                data: {
                    title: titleText,
                    body: bodyText,
                    senderId: String(userId),
                    type: "MATCH_CONFIRMED",
                    date: String(dateString)
                },
                topic: creatorTopic
            });

            // Envoyer "Alors, ce match ?" IMMÉDIATEMENT aux premium
            const allParticipants = [userId, ...(participantIds || [])];
            for (const pid of allParticipants) {
                const profileDoc = await admin.firestore().collection("profiles").doc(pid).get();
                const profile = profileDoc.data();
                if (profile && profile.premium === true) {
                    const safeUid = pid.replace(/[^a-zA-Z0-9-_.~%]/g, "");
                    await admin.messaging().send({
                        data: {
                            title: "🎾 Alors, ce match ?",
                            body: `As-tu joué ce ${dateString} ? Dis-nous tout pour tes stats !`,
                            type: "CHECK_MATCH_PLAYED",
                            date: String(dateString),
                            availabilityId: String(event.params.id)
                        },
                        topic: `user_${safeUid}`
                    });
                }
            }
        }
    } catch (err) { console.error(err); }
    return null;
});

// 5. NOTIFICATION INVITATION DUO (CRÉATION ET MISES À JOUR)
exports.onDuoInvitationCreated = onDocumentCreated("duo_invitations/{id}", async (event) => {
    const data = event.data.data();
    if (!data) return null;
    const safeUid = data.toId.replace(/[^a-zA-Z0-9-_.~%]/g, "");
    const topicName = `user_${safeUid}`;
    try {
        await admin.messaging().send({
            data: {
                title: "🎾 Invitation Duo !",
                body: `${data.fromName} te propose un duo pour le ${data.date}.`,
                senderId: data.fromId,
                type: "DUO_INVITATION",
                date: String(data.date)
            },
            topic: topicName
        });
    } catch (error) { console.error(error); }
    return null;
});

exports.onDuoInvitationUpdated = onDocumentUpdated("duo_invitations/{id}", async (event) => {
    const newData = event.data.after.data();
    const oldData = event.data.before.data();
    if (!newData || !oldData) return null;

    // Déterminer qui doit recevoir la notification
    const recipientId = (newData.lastProposerId === newData.fromId) ? newData.toId : newData.fromId;
    const safeUid = recipientId.replace(/[^a-zA-Z0-9-_.~%]/g, "");
    const topicName = `user_${safeUid}`;

    let title = "🎾 Agenda Padel";
    let body = "";

    if (newData.status === "PROPOSED" && newData.status !== oldData.status) {
        title = "⏳ Contre-proposition Duo";
        body = `${newData.lastProposerId === newData.fromId ? newData.fromName : newData.toName} propose un nouvel horaire pour le ${newData.date} (${newData.proposedStartTime} - ${newData.proposedEndTime}).`;
    } else if (newData.status === "CONFIRMED" && newData.status !== oldData.status) {
        title = "✅ Duo Confirmé !";
        body = `Ton match en duo pour le ${newData.date} à ${newData.proposedStartTime} - ${newData.proposedEndTime} est validé.`;
    } else if (newData.status === "DECLINED" && newData.status !== oldData.status) {
        title = "❌ Duo Refusé / Annulé";
        body = `La demande de duo pour le ${newData.date} a été déclinée.`;
    } else {
        return null;
    }

    try {
        await admin.messaging().send({
            data: {
                title: title,
                body: body,
                senderId: newData.lastProposerId,
                type: "DUO_UPDATE",
                date: String(newData.date)
            },
            topic: topicName
        });
    } catch (error) { console.error(error); }
    return null;
});

// 6. NOTIFICATION SUPPRESSION SÉANCE
exports.onAvailabilityDeleted = onDocumentDeleted("creneaux/{id}", async (event) => {
    const deletedData = event.data.data();
    if (!deletedData) return null;
    const { dateString, personName, groupId, userId } = deletedData;
    try {
        const snapshot = await admin.firestore().collection("creneaux")
            .where("dateString", "==", dateString)
            .where("groupId", "==", groupId || "")
            .get();
        const maxAfter = calculateMaxConcurrent(snapshot.docs.map(doc => doc.data()));
        const topicName = `match_${groupId || "default"}_${dateString}`.replace(/[:\-]/g, "_");

        if (maxAfter < 4 && snapshot.docs.length >= 3) {
            await admin.messaging().send({
                data: {
                    title: "⚠️ Match incomplet",
                    body: `Suite au départ de ${personName}, il manque du monde pour le ${dateString}.`,
                    senderId: String(userId),
                    type: "MATCH_INCOMPLETE"
                },
                topic: topicName
            });
        }
    } catch (err) { console.error(err); }
    return null;
});

// 7. NOTIFICATION DE VÉRIFICATION LE LENDEMAIN (À 09:00 chaque jour)
exports.checkYesterdayMatches = onSchedule("0 9 * * *", async (event) => {
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    const dateStr = yesterday.toISOString().split('T')[0];

    try {
        const snapshot = await admin.firestore().collection("creneaux")
            .where("dateString", "==", dateStr)
            .get();

        const processedUsers = new Set();

        for (const doc of snapshot.docs) {
            const data = doc.data();
            // On vérifie si c'était un match confirmé (Duo ou 4 joueurs)
            if (data.isDuo || (data.participantIds && data.participantIds.length >= 4)) {
                const usersToNotify = [data.userId, ...(data.participantIds || [])];
                for (const userId of usersToNotify) {
                    if (!processedUsers.has(userId)) {
                        const safeUid = userId.replace(/[^a-zA-Z0-9-_.~%]/g, "");
                        const topicName = `user_${safeUid}`;

                        await admin.messaging().send({
                            data: {
                                title: "🎾 Alors, ce match ?",
                                body: `As-tu joué ton Padel hier (${dateStr}) ? Dis-nous tout pour tes stats !`,
                                type: "CHECK_MATCH_PLAYED",
                                dateString: String(dateStr),
                                availabilityId: String(doc.id)
                            },
                            topic: topicName
                        });
                        processedUsers.add(userId);
                    }
                }
            }
        }
    } catch (error) {
        console.error("Error in scheduled function:", error);
    }
});
