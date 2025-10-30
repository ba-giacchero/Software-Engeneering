import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class BitPacking {

    public interface BitPacker {
        void compress(int[] input);
        void decompress(int[] out);
        int get(int i);
        int length();
    }
    // fonctions utilitaires pour le bit-packing elles sont statiques car indépendantes des instances ce qui permet de factoriser le code   
    // nombre de bits nécessaires pour représenter v (v >= 0)
    static int bitsNeeded(int v) {
        if (v < 0) throw new IllegalArgumentException("Seulement les entiers positifs ou nuls sont supportés");
        return 32 - Integer.numberOfLeadingZeros(v);
    }
    // masque avec les k bits de poids faible à 1
    static int maskLow(int k) {
        if (k >= 32) return ~0;
        return (1 << k) - 1;
    }

    
    public static class CrossBitPacker implements BitPacker {
        private int n;              // nombre d'entiers
        private int k;               // bits par valeur
        private int[] compresse;        // stockage compressé 

        @Override
        public void compress(int[] tab) {
            this.n = tab.length;      // nombre d'entiers a compresser
            int max = 0;                 // valeur maximale
            for (int v : tab) {
                if (v < 0) throw new IllegalArgumentException("seulement les entiers positifs ou nuls sont supportés");
                if (v > max) {
                    max = v;
                }
            }
            this.k = bitsNeeded(max);
            if (k > 32) {
                k = 32; 
            }
            int totalbits = n * k;
            int taillesortie = (totalbits + 31) / 32;
            compresse = new int[taillesortie];
            for (int i = 0; i < n; i++) {
                int val = tab[i] & maskLow(k);
                int bitpos = i * k;
                int word = bitpos / 32;
                int offset = bitpos % 32;
                if (offset + k <= 32) {
                    // rentre entièrement dans un seul int
                    compresse[word] |= (val << offset);
                } else {
                    // divisé sur deux ints
                    int nbrbitsfaible = 32 - offset;
                    int lowMask = maskLow(nbrbitsfaible);
                    int poidsFaible = val & lowMask;
                    int poidsFort = val >>> nbrbitsfaible;
                    compresse[word] |= (poidsFaible << offset);
                    compresse[word + 1] |= poidsFort;
                }
            }
        }

        @Override
        public void decompress(int[] tab) {
            if (tab.length != n) throw new IllegalArgumentException("Taille du tableau non valide");
            for (int i = 0; i < n; i++) {
                tab[i] = get(i);
            }
        }

        @Override
        public int get(int i) {
            if (i < 0 || i >= n) throw new IndexOutOfBoundsException();
            int bitpos = i * k;
            int word = bitpos / 32;
            int offset = bitpos % 32;

            if (offset + k <= 32) {
                // rentre entièrement dans un seul int
                int w = compresse[word];
                return (w >>> offset) & maskLow(k);
            } else {
                // divisé sur deux ints
                int nbrbitsfaible = 32 - offset;
                int poidsFaible = (compresse[word] >>> offset) & maskLow(nbrbitsfaible);
                int poidsFort = compresse[word + 1] & maskLow(k - nbrbitsfaible);
                return (poidsFort << nbrbitsfaible) | poidsFaible;
            }
        }

        @Override
        public int length() { 
            return n; 
        }

        public int getK() { 
            return k; 
        }
        public int[] getPacked() { 
            return compresse; 
        }
    }

    public static class NoCrossBitPacker implements BitPacker {
        private int n;
        private int k;
        private int[] compresse;
        private int taillecompressée;


        @Override
        public void compress(int[] tab) {
            // déterminer n et k
            this.n = tab.length;
            int max = 0;
            // trouver la valeur maximale
            for (int v : tab) {
                if (v < 0) throw new IllegalArgumentException("Nombre négatif non supporté");
                if (v > max) max = v;
            }
            // déterminer k en fonction de la valeur maximale
            this.k = bitsNeeded(max);
            if (k > 32) k = 32;

            // calculer taillecompressée afin de ne pas traverser les mots
            taillecompressée = 32 / k;
            if (taillecompressée == 0) taillecompressée = 1; // si k > 32, mais normalement k<=32

            int outLen = (n + taillecompressée - 1) / taillecompressée;
            compresse = new int[outLen];
            // compresser chaque valeur
            for (int i = 0; i < n; i++) {
                int word = i / taillecompressée;
                int slot = i % taillecompressée;
                int offset = slot * k;
                int val = tab[i] & maskLow(k);
                compresse[word] |= (val << offset);
            }
        }

        @Override
        public void decompress(int[] tab) {
            // vérifier la longueur du tableau de destination
            if (tab.length != n) throw new IllegalArgumentException("Longueur de destination non valide");
            // décompresser chaque valeur
            for (int i = 0; i < n; i++) {
                tab[i] = get(i);
            }
        }

        @Override
        public int get(int i) {
            // accéder à la ième valeur décompressée
            if (i < 0 || i >= n) throw new IndexOutOfBoundsException();
            int word = i / taillecompressée;
            int slot = i % taillecompressée;
            int offset = slot * k;
            int w = compresse[word];
            return (w >>> offset) & maskLow(k);
        }

        @Override
        public int length() { 
            return n; 
        }

        public int getK() { 
            return k; 
        }

        public int[] getPacked() { 
            return compresse; 
        }
    }

    // ---------- Overflow packer ----------
    // On cherche une petite taille petitK (0..32) qui minimise : totalBits = n * bitsChamp + nbDebordement * 32
    // où bitsChamp = 1 + max(petitK, bitsNeeded(nbDebordement-1))
    // totalBits est la taille totale en bits du stockage compressé donc doit etre minimisé afin d'avoir une bonne compression
    // On stocke un en-tête (n, petitK, bitsChamp, nbDebordement), puis les champs compressés (n champs de bitsChamp),
    // puis la zone overflow (nbDebordement int réels).
    public static class OverflowBitPacker implements BitPacker {
        private int taille; // nombre d'entiers à compresser
        private int petitK; // petitK choisi par la fonction de compression
        private int bitsChamp; // bits par champ (1 bit de flag + reste)
        private int nbDebordement; // nombre de débordements (valeurs qui ne rentrent pas pour petitK donné)
        private int[] valeursDebordement; // taille nbDebordement
        private int[] champsPackes;   // champsPackés contient les flags et les contenus

        @Override
        public void compress(int[] input) {
            this.taille = input.length;
            // calculer bitsnecessaires pour chaque élément dans un tableau
            int[] bitsnecessaires = new int[taille];
            int bitsMax = 0;
            for (int i = 0; i < taille; i++) {
                bitsnecessaires[i] = bitsNeeded(input[i]);
                if (bitsnecessaires[i] > bitsMax) {
                    bitsMax = bitsnecessaires[i];
                }
            }

            // on cherche petitK donc on creer une boucle pour tester chaque candidatK
            long meilleurTotal = Long.MAX_VALUE;
            int meilleurK = 0;
            int meilleurBitsChamp = 0;
            int meilleurNbDebordement = 0;
            
            for (int candidatK = 0; candidatK <= Math.max(1, bitsMax); candidatK++) {
                int nbOverflow = 0;
                for (int b : bitsnecessaires) {
                    if (b > candidatK) nbOverflow++;
                }
                //on calcule le nombre de valeur qui iront dans la zone Overflow
                int bitsIndice = (nbOverflow > 0) ? bitsNeeded(nbOverflow - 1) : 0;//on donne le nombre de bits nécessaires pour indexer la zone overflow
                int bitsChampCand = 1 + Math.max(candidatK, bitsIndice); // 1 bit de flag + reste
                long totalBitsCand = (long) taille * bitsChampCand + (long) nbOverflow * 32; // ici on ajoute le coût de la zone overflow
                if (totalBitsCand < meilleurTotal) {
                    meilleurTotal = totalBitsCand;
                    meilleurK = candidatK;
                    meilleurBitsChamp = bitsChampCand;
                    meilleurNbDebordement = nbOverflow;
                }// ce qui minimise le total et la taille de la zone overflow
            }
            // on a trouvé le meilleur petitK
            // on les assigne aux champs de l'objet
            this.petitK = meilleurK;
            this.bitsChamp = meilleurBitsChamp;
            this.nbDebordement = meilleurNbDebordement;

            // on creer la zone de débordement et le mapping 
            // pour chaque occurrence de débordement, l'index dans la zone overflow 
            // et la valeur réelle est stockée dans valeursDebordement
            List<Integer> listeDebordement = new ArrayList<>(nbDebordement);
            for (int i = 0; i < taille; i++) {
                if (bitsnecessaires[i] > petitK) {
                    listeDebordement.add(input[i]);
                }
            }//on remplit le tableau valeursDebordement
            this.valeursDebordement = new int[listeDebordement.size()];
            for (int i = 0; i < valeursDebordement.length; i++) {
                valeursDebordement[i] = listeDebordement.get(i);
            }

            // lie chaque position d'entrée à un index de débordement si nécessaire
            // on doit mapper les occurrences aux indices : on va itérer sur l'entrée et quand une valeur nécessite un débordement, on assigne l'index suivant.
            int[] indiceDebordementParPos = new int[taille];
            Arrays.fill(indiceDebordementParPos, -1);
            int prochainIndiceDebordement = 0;
            for (int i = 0; i < taille; i++) {
                if (bitsnecessaires[i] > petitK) {
                    indiceDebordementParPos[i] = prochainIndiceDebordement++;
                }
            }

            // allouer le tableau de champs packés puis l'initialiser
            int totalBits = taille * bitsChamp;
            int outLen = (totalBits + 31) / 32;
            champsPackes = new int[outLen];
            Arrays.fill(champsPackes, 0);
            int affectationDebordement = 0;
            for (int i = 0; i < taille; i++) {
                int flag;
                int contenu;
                if (bitsnecessaires[i] > petitK) {
                    flag = 1;
                    contenu = affectationDebordement++;
                } else {
                    flag = 0;
                    contenu = input[i] & maskLow(bitsChamp - 1);
                }
                int valeurChamp = (contenu << 1) | flag;
                int posBits = i * bitsChamp;
                int mot = posBits / 32;
                int decalage = posBits % 32;
                // cas ou le champ rentre dans un seul mot
                if (decalage + bitsChamp <= 32) {
                    champsPackes[mot] |= (valeurChamp << decalage);
                // divisé sur deux mots
                } else {
                    int lowBits = 32 - decalage;
                    int poidsFaible = valeurChamp & maskLow(lowBits);
                    int poidsFort = valeurChamp >>> lowBits;
                    champsPackes[mot] |= (poidsFaible << decalage);
                    champsPackes[mot + 1] |= poidsFort;
                }
            }
        }

        @Override
        public void decompress(int[] out) {
            if (out.length != taille) throw new IllegalArgumentException("longueur de destination non valide");
            // parcourir les champs packés et décompresser
            for (int i = 0; i < taille; i++) {
                int posBits = i * bitsChamp;
                int mot = posBits / 32;
                int decalage = posBits % 32;
                int valeurChamp;
                // cas ou le champ rentre dans un seul mot
                if (decalage + bitsChamp <= 32) {
                    valeurChamp = (champsPackes[mot] >>> decalage) & maskLow(bitsChamp);
                } else {
                    // divisé sur deux mots
                    int lowBits = 32 - decalage;
                    int poidsFaible = (champsPackes[mot] >>> decalage) & maskLow(lowBits);
                    int poidsFort = champsPackes[mot + 1] & maskLow(bitsChamp - lowBits);
                    valeurChamp = (poidsFort << lowBits) | poidsFaible;
                }
                int flag = valeurChamp & 1;
                int contenu = valeurChamp >>> 1;
                if (flag == 0) {
                    // cas normal
                    out[i] = contenu;
                } else {
                    // cas de débordement
                    out[i] = valeursDebordement[contenu];
                }
            }
        }

        @Override
        public int get(int i) {
            // accéder à la ième valeur décompressée
            if (i < 0 || i >= taille) throw new IndexOutOfBoundsException();
            int posBits = i * bitsChamp;
            int mot = posBits / 32;
            int decalage = posBits % 32;
            int valeurChamp;
            if (decalage + bitsChamp <= 32) {
                // cas ou le champ rentre dans un seul mot
                valeurChamp = (champsPackes[mot] >>> decalage) & maskLow(bitsChamp);
            } else {
                // divisé sur deux mots
                int lowBits = 32 - decalage;
                int poidsFaible = (champsPackes[mot] >>> decalage) & maskLow(lowBits);
                int poidsFort = champsPackes[mot + 1] & maskLow(bitsChamp - lowBits);
                valeurChamp = (poidsFort << lowBits) | poidsFaible;
            }
            int flag = valeurChamp & 1;
            int contenu = valeurChamp >>> 1;
            if (flag == 0) {
                // cas normal
                return contenu;
            } else {
                // cas de débordement
                return valeursDebordement[contenu];
            }
        }

        @Override
        public int length() { return taille; }

        public int getSmallK() { return petitK; }
        public int getFieldBits() { return bitsChamp; }
        public int getOverflowCount() { return nbDebordement; }
        public int[] getOverflowValues() { return valeursDebordement; }
        public int[] getPackedFields() { return champsPackes; }
    }

    // ---------- Factory ----------
    public static class BitPackerFactory {
        public enum Type { CROSS, NOCROSS, OVERFLOW }

        public static BitPacker create(Type t) {
            return switch (t) {
                case CROSS -> new CrossBitPacker();
                case NOCROSS -> new NoCrossBitPacker();
                case OVERFLOW -> new OverflowBitPacker();
            };
        }
    }

    // ---------- Benchmark ----------
    public static class Bench {
        // warmup runs pour que java "chauffe" la VM afin d'optimiser le code
        // mesure répétée (mesurer le temps total puis moyenne) afin d'avoir une bonne estimation
        //on compte le temps en nanosecondes
        
        public static long chronoNs(Runnable r, int repeats) {
            long start = System.nanoTime();
            for (int i = 0; i < repeats; i++) r.run();
            long end = System.nanoTime();
            return end - start;
        }

        //fonction de warmup 
        public static void Benchmark(BitPacker packer, int[] data, int iterations) {
        for (int w = 0; w < 20; w++) {
                packer.compress(data);
            }

        // bloc de mesure de la compression
        long tCompression = chronoNs(() -> packer.compress(data), iterations);
        System.out.printf("compression : total %d ns, moyenne %.2f µs\n",
            tCompression, (tCompression / 1000.0) / iterations);

            // bloc de mesure de get (accès aléatoire via seed fixe)
            int n = data.length;
            Random rnd = new Random(123);
            long tGet = chronoNs(() -> {
                int s = 0;
                for (int q = 0; q < 1000; q++) {
                    int idx = rnd.nextInt(n);
                    s += packer.get(idx);
                }
                if (s == -1) System.out.println("impossible");
            }, iterations);
        System.out.printf("get de 1000 valeurs aléatoires : total %d ns, moyenne %.2f ms par itération\n",
            tGet, (tGet / 1000000.0) / (double) iterations);

            // bloc de mesure de la décompression 
            int[] dest = new int[n];
            long tDecompress = chronoNs(() -> {
                packer.decompress(dest);
            }, iterations);
        System.out.printf("décompression : total %d ns, moyenne %.2f µs\n",
            tDecompress, (tDecompress / 1000.0) / iterations);

            // on verifie si l'algo est bon sur la dernière décompression
            int[] dest2 = new int[n];
            packer.decompress(dest2);
            boolean ok = Arrays.equals(dest2, data);
        System.out.println("Vérification : décompression == original : " + ok);
        }
    }

    public static void main(String[] args) {
        System.out.println("BitPacking - démonstration");

    // générer les données de test
    int tailleExemple = 10000; 
    int[] donnees = new int[tailleExemple];
        //mélange de petites valeurs et quelques grandes pour l'overflow
        Random rnd = ThreadLocalRandom.current();
        for (int i = 0; i < tailleExemple; i++) {
            if (i % 500 == 0) donnees[i] = 1 << (10 + (i/500)%5); // quelques grandes valeurs
            else donnees[i] = rnd.nextInt(50) + 1; // petites valeurs strictement positives (1..50)
            donnees[1] = 0; // ajouter une valeur zéro pour tester
        }

        // CROSS
        System.out.println("\n--- CROSS ---");
        BitPacker cross = BitPackerFactory.create(BitPackerFactory.Type.CROSS);
    cross.compress(donnees);
    System.out.printf("n=%d, k=%d\n", cross.length(), ((CrossBitPacker)cross).getK());
    Bench.Benchmark(cross, donnees, 5);

        // NOCROSS
        System.out.println("\n--- NOCROSS ---");
        BitPacker nocross = BitPackerFactory.create(BitPackerFactory.Type.NOCROSS);
    nocross.compress(donnees);
    System.out.printf("n=%d, k=%d\n", nocross.length(), ((NoCrossBitPacker)nocross).getK());
    Bench.Benchmark(nocross, donnees, 5);

        // OVERFLOW
        System.out.println("\n--- OVERFLOW ---");
        OverflowBitPacker overflow = (OverflowBitPacker) BitPackerFactory.create(BitPackerFactory.Type.OVERFLOW);
    overflow.compress(donnees);
    System.out.printf("n=%d, petitK=%d, bitsChamp=%d, nbDebordement=%d\n",
        overflow.length(), overflow.getSmallK(), overflow.getFieldBits(), overflow.getOverflowCount());
    Bench.Benchmark(overflow, donnees, 5);

        // Exemples get()
        System.out.println("\nExemples get(i) :");
    for (int i = 0; i < 5; i++) {
        System.out.printf("i=%d original=%d cross.get=%d nocross.get=%d overflow.get=%d\n",
            i, donnees[i], cross.get(i), nocross.get(i), overflow.get(i));
    }
    for (int i = 500; i < 505; i++) {
        System.out.printf("i=%d original=%d cross.get=%d nocross.get=%d overflow.get=%d\n",
            i, donnees[i], cross.get(i), nocross.get(i), overflow.get(i));
    }

        System.out.println("\nFin démonstration.");
    }
}
