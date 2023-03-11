VoltageControlStatus {
   OUTDATED, // statut plus a jour
   DISABLED, // ne peut controller la tension a cause de l'etat local du control (disabled ou plus de controlleurs) 
   MERGED // mergé avec un autre control du meme type
   SHADOWED, // masqué par un autre control d'un type plus prioritaire (generator -> transfo -> shunt)
   ENABLED, // tiens la tension
}

Les voltage control ont un lien vers les autres voltage control avec qui ils ont été mergé
ils ont un statut
mis a jour du statut a partir du bus controllé
invalidation a partir du bus controllé et suivant les merges

donc a partir d'un bus controllé, on a possiblement plusieurs generators voltage control,
plus plusieurs transfo voltage control, plus plusieurs shunt voltage control.

update (
   - a partir du bus controllé primaire, on repasse le statut a OUTDATED ainsi que tous les bus controllés des voltages 
control mergés
   - a partir du bus controllé primaire, on liste les bus controllés de la plaque non impedante
   - on calcul l'état local des voltage controls ENABLED ou DISABLED
   - sur ceux qui sont valides, on classe par ordre de priorité sur les bus id par control du meme type (generator, shunt ou transfo)
   - le premier ENABLED liste les autres ENABLED et les passe en MERDED
   - sur tout ceux qui reste ENABLED (normalement max 1 par type), on ne laisse que le plus prioritaire a ENABLED et les 
autres a SHADOWED

)

du coup dans l'update du equation system, on a moins de truc a tester, et on doit donc se reposer sur le VoltageControlStatus
normalement pas de OUTDATED. On ne traite ques les controlled bus en etat ENABLED et on suit les liens vers les MERGED pour 
rajouter les controlleurs
