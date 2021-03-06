import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.json.simple.*;

public class ClientThread
        extends Thread {

    private Socket clientSocket;
    private String pseudo;
    private Map<User, Socket> listeClients;
    private ArrayList<Groupe> listeGroupes;
    private JSONArray jsonHistorique;
    private JSONArray jsonMessagesGroupes;
    private ReentrantLock mutex;
    private ReentrantLock mutexGroupe;
    private JSONArray listeGroupsPersistant ; // liste des groupes existants



    ClientThread(Socket s, String pseudo, Map<User, Socket> liste, JSONArray jsonHistorique, ReentrantLock mutex, ReentrantLock mutexGroupe, ArrayList<Groupe> listeGrpes, JSONArray jsonMessagesGroupes, JSONArray listeGroupsPersistant){
        this.listeClients = liste;
        this.listeGroupes=listeGrpes;
        this.pseudo = pseudo;
        this.clientSocket = s;
        this.mutex = mutex;
        this.jsonHistorique = jsonHistorique;
        this.jsonMessagesGroupes= jsonMessagesGroupes;
        this.mutexGroupe = mutexGroupe;
        this.listeGroupsPersistant=listeGroupsPersistant;
    }


    public void run() {
        boolean inLine = true;
        try {
            if(inLine) {
                //initialisation des variables
                BufferedReader socIn;
                socIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintStream socOut = new PrintStream(clientSocket.getOutputStream());
                User userActuel = getUserByPseudo(pseudo, listeClients);
                String interlocuteur = "";
                while (true) {
                    String line = socIn.readLine();
                    if (line.equals("Afficher listeClients")) // si le client a demandé à voir la liste des clients
                    {
                        callAfficherListeClients(socOut);
                    }else if(line.equals("Afficher listeGroupes"))
                    {
                        callAfficherListeGroupes(socOut);
                    } else if (line.equals("déconnexion"))
                    {
                        userActuel.setEtat(false);
                        inLine = false;
                        System.out.println("Deconnexion from " + clientSocket.getLocalAddress());
                        break;
                    } else if (line.length() >= 2 && line.startsWith("1:") && !line.substring(2).equals("**"))
                    {
                        interlocuteur = line.substring(2);
                        callChoixInterlocuteur(line, socOut);
                    } else if (line.length() >= 2 && line.startsWith("1bis:") && !line.substring(5).equals("**"))
                    {
                        interlocuteur = "group_name" + line.substring(5); // ici interlocuteur = nom groupe
                        callChoixGroupe(line, socOut);
                    } else if (line.length() >= 9 && line.startsWith("pour tous"))
                    {
                        interlocuteur = "tous";
                    } else if (line.length() >= 12 && line.startsWith("Conversation"))
                    {
                        callAfficherConversation(line, socOut);
                    } else if (line.length() >= 12 && line.startsWith("GroupeConversation")) {
                        callAfficherGroupeConversation(line, socOut);
                    }else if(line.length()>= 11 && line.startsWith("creerGroupe"))
                    {
                        callCreerGroupe(line.substring(11),socOut);
                    } else {
                        //DISCUSSION BASIQUE
                        if (!interlocuteur.equals("tous") && !interlocuteur.startsWith("group_name"))
                        {
                            callParlerAQuelquun(line, interlocuteur);
                        } else if(!interlocuteur.equals("tous") && interlocuteur.startsWith("group_name"))
                        {
                            callParlerAGroupe(line, interlocuteur.substring(10));
                        }else{
                            callParlerATous(line);
                        }

                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error in EchoServer:" + e);
        }
    }


    public static User getUserByPseudo(String pseudo, Map<User, Socket> liste) {
        User userPrec = null;
        for (Map.Entry<User, Socket> entry : liste.entrySet()) {
            if (entry.getKey().getPseudo().equals(pseudo)) {
                userPrec = entry.getKey();
                break;
            }
        }
        return userPrec;
    }

    public static Groupe getGroupByName(String name, ArrayList<Groupe> listeGroupes) {
        Groupe groupePrec = null;
        for (Groupe groupe : listeGroupes) {
            if (groupe.getName().equals(name)) {
                groupePrec = groupe;
                break;
            }
        }
        return groupePrec;
    }

    public void fillJson(String interlocuteur, String line) {
        JSONObject elementsMessage = new JSONObject();
        elementsMessage.put("expediteur", pseudo);
        elementsMessage.put("destinataire", interlocuteur);
        elementsMessage.put("contenu", line);
        jsonHistorique.add(elementsMessage);
    }

    public void fillJsonMessagesGroupe(String nomGroupe, String line) {
        JSONObject elementsMessageGroupe = new JSONObject();
        elementsMessageGroupe.put("expediteur", pseudo);
        elementsMessageGroupe.put("destinataire", nomGroupe);
        elementsMessageGroupe.put("contenu", line);
        jsonMessagesGroupes.add(elementsMessageGroupe);
    }

    public void fillJsonGroupe(String nomGroupe) {
        JSONObject elementsGroupe = new JSONObject();
        JSONArray membres = new JSONArray();
        JSONObject unMembre = new JSONObject();
        unMembre.put("pseudo",pseudo);
        membres.add(unMembre);
        elementsGroupe.put("nomGroupe", nomGroupe);
        elementsGroupe.put("membres", membres);
        listeGroupsPersistant.add(elementsGroupe);
        parseGroupeUsers();
    }

    // ajoute un utilisateur à la liste des utilisateurs persistante
    public void fillListePersistanteUserGroupe(String nomGroupeChoisi) {
        //add en place
        for(Object object : listeGroupsPersistant){
            JSONObject objectInArray = (JSONObject) object;
            String nomGroupe= objectInArray.get("nomGroupe").toString();
            if(nomGroupe.equals(nomGroupeChoisi)){
                JSONArray arrayMembres = (JSONArray) objectInArray.get("membres");
                JSONObject o = new JSONObject();
                o.put("pseudo",pseudo);
                arrayMembres.add(o);
            }
        }
        parseGroupeUsers(); //Exporter le fichier JSON
    }


    public void parseGroupeUsers() {
        try (FileWriter file = new FileWriter("./groupes.json")) {
            file.write(listeGroupsPersistant.toJSONString());
            file.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void parse() {
        try (FileWriter file = new FileWriter("./historique.json")) {
            file.write(jsonHistorique.toJSONString());
            file.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void parseGroupeMessages() {
        try (FileWriter file = new FileWriter("./messagesGroupe.json")) {
            file.write(jsonMessagesGroupes.toJSONString());
            file.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public String afficherConversation(String contact) {
        StringBuilder listeHistorique = new StringBuilder();
        for (Object element : jsonHistorique) {
            JSONObject objectInArray = (JSONObject) element;
            String destinataire = (String) objectInArray.get("destinataire");
            String expediteur = (String) objectInArray.get("expediteur");
            if ((contact.equals(destinataire) && pseudo.equals(expediteur)) || (contact.equals(expediteur) && pseudo.equals(destinataire))) {
                listeHistorique.append(expediteur).append(" : ").append(objectInArray.get("contenu")).append("\n");
            }
        }
        listeHistorique.append(" ");
        return listeHistorique.substring(0,listeHistorique.length()-1);
    }


    public String afficherGroupeConversation(String groupName) {
        StringBuilder listeHistorique = new StringBuilder();
        for (Object elementGroup : jsonMessagesGroupes) {
            JSONObject objectInArray = (JSONObject) elementGroup;
            String destinataire = (String) objectInArray.get("destinataire");
            String expediteur = (String) objectInArray.get("expediteur");
            if (groupName.equals(destinataire)) {
                listeHistorique.append(expediteur).append(" : ").append(objectInArray.get("contenu")).append("\n");
            }
        }
        listeHistorique.append(" ");
        return listeHistorique.substring(0,listeHistorique.length()-1);
    }

    public void callAfficherListeClients(PrintStream socOut){
        StringBuilder listeToPrint = new StringBuilder();
        if((listeClients.size()-1)==0){
            socOut.println("isEmpty");
        }else {
            for (Map.Entry<User, Socket> entry : listeClients.entrySet()) {
                if (!entry.getKey().getPseudo().equals(pseudo)) {
                    listeToPrint.append("	-").append(entry.getKey().getPseudo());
                    if (entry.getKey().getEtat()) {
                        listeToPrint.append(" (en ligne)\n");
                    } else {
                        listeToPrint.append(" (hors ligne)\n");
                    }
                }
            }
            socOut.println("\u001B[33mlistToPrint\u001B[0m" + listeToPrint + "\u001B[33m\nA qui voulez-vous parler?\u001B[0m");
        }
    }



    public void callAfficherListeGroupes(PrintStream socOut){

        StringBuilder listeToPrint = new StringBuilder();
        if(listeGroupes.isEmpty()){
            socOut.println("isEmpty");
        }else {
            for (Groupe groupe : listeGroupes) {
                listeToPrint.append("  -").append(groupe.getName()).append("\n");
                //aficher les membres du groupe
                for (String p : groupe.getMembres()) {
                    listeToPrint.append("\t*").append(p).append("\n");
                }
                listeToPrint.append("\n");
            }
            socOut.println("\u001B[33mlistToPrint\u001B[0m" + listeToPrint + "\u001B[33m\nA qui voulez-vous parler?\u001B[0m");
        }

    }


    public void callChoixInterlocuteur(String line, PrintStream socOut){
        //le client a choisi quelqu'un a qui parler
        String interlocuteur = line.substring(2);
        if (!listeClients.containsKey(getUserByPseudo(interlocuteur, listeClients))) {
            socOut.println("user_not_found");
        }
    }

    public void callChoixGroupe(String line, PrintStream socOut){
        //le client a choisi quelqu'un a qui parler
        String groupe = line.substring(5);
        if (!listeGroupes.contains(getGroupByName(groupe, listeGroupes))) {
            socOut.println("group_not_found");
        }
    }

    public void callAfficherConversation(String line, PrintStream socOut){
        String contact = line.substring(12);
        if (!listeClients.containsKey(getUserByPseudo(contact, listeClients))) {
            socOut.println();
        } else {
            try {
                mutex.lock();
                    socOut.println("\n\n\n\n\u001B[32m"+afficherConversation(contact)+"\u001B[0m");
            } finally {
                mutex.unlock();
            }
        }
    }

    public void callAfficherGroupeConversation(String line, PrintStream socOut){
        String nomGroupe = line.substring(18);
        Groupe group = getGroupByName(nomGroupe, listeGroupes);
        if (!listeGroupes.contains(group)) { // vérifier que le groupe existe bien
            socOut.println();
        } else if (!listeGroupeContientPseudo(group.getMembres(), pseudo)) { // si l'utilisateur ne fait pas encore partie du groupe on le rajoute
            group.addMember(pseudo);
            try {
                mutexGroupe.lock();
                fillListePersistanteUserGroupe(nomGroupe);
            } finally {
                mutexGroupe.unlock();
            }
            try {
                mutexGroupe.lock();
                socOut.print("\n\n\n\u001B[35m"+afficherGroupeConversation(nomGroupe)+"\u001B[0m");
            } finally {
                mutexGroupe.unlock();
            }
        }else{  // l'utilisateur fait déjà partie du groupe
            try {
                mutexGroupe.lock();
                    socOut.println("\n\n\n\u001B[35m"+afficherGroupeConversation(nomGroupe)+"\u001B[0m");
            } finally {
                mutexGroupe.unlock();
            }
        }
    }

    public void callParlerATous(String line) throws IOException {
        for (Map.Entry<User, Socket> entry : listeClients.entrySet()) {
            if (!entry.getKey().getPseudo().equals(pseudo)) {
                if(entry.getKey().getEtat()) {
                    PrintStream socOutClients = new PrintStream(entry.getValue().getOutputStream());
                    socOutClients.println("\u001B[36m(A tout le monde) " + pseudo + " : " + line + "\u001B[0m");
                }
            }
        }
    }


    public void callParlerAQuelquun(String line, String interlocuteur) throws IOException {
        for (Map.Entry<User, Socket> entry : listeClients.entrySet()) {
            if (entry.getKey().getPseudo().equals(interlocuteur)) {
                if(entry.getKey().getEtat()) {
                    PrintStream socOutClients = new PrintStream(entry.getValue().getOutputStream());
                    socOutClients.println("\u001B[32m"+ pseudo + " : " + line+"\u001B[0m");
                }
                try {
                    mutex.lock();
                    fillJson(interlocuteur, line);
                    parse(); //Exporter le fichier JSON
                } finally {
                    mutex.unlock();
                }
                break;
            }
        }
    }

    public void callParlerAGroupe(String line, String nomGroupe) throws IOException {
        PrintStream socOutClients = null;
        for (Groupe groupe : listeGroupes) {
            if (groupe.getName().equals(nomGroupe)) { //vérifier le bon groupe
                ArrayList<String> listUsers = groupe.getMembres();
                for(String p : listUsers){ // envoyer aux bons membres du groupe
                    if(getUserByPseudo(p,listeClients).getEtat() && !(p.equals(pseudo))) {
                        //on récupère la socket client
                        for (Map.Entry<User, Socket> entry : listeClients.entrySet()) {
                            if (entry.getKey().getPseudo().equals(p)) {
                                socOutClients = new PrintStream(entry.getValue().getOutputStream());
                                break;
                            }
                        }
                        socOutClients.println("\u001B[35m(" + nomGroupe + ") " + pseudo + " : " + line+"\u001B[0m");
                    }
                }
                try {
                    mutexGroupe.lock();
                    fillJsonMessagesGroupe(nomGroupe, line); //pseudo ici = expéditeur dans jsonMessageGroupes
                    parseGroupeMessages(); //Exporter le fichier JSON
                } finally {
                    mutexGroupe.unlock();
                }
                break;
            }
        }
    }

    public void callCreerGroupe(String nomGroupe, PrintStream socOut){
        boolean nonContient = true;
        for(Groupe g : listeGroupes){
            if(g.getName().equals(nomGroupe)){
                nonContient=false;
            }
        }
        if(nonContient){
            ArrayList<String> membre = new ArrayList<>();
            membre.add(pseudo);
            Groupe g = new Groupe(nomGroupe,membre);
            listeGroupes.add(g);
            try {
                mutexGroupe.lock();
                fillJsonGroupe(nomGroupe);
            } finally {
                mutexGroupe.unlock();
            }
            socOut.println("group_created");
        }else{ // un nom du meme groupe existe déjà
            socOut.println("group_created_error");
        }
    }

    public boolean listeGroupeContientPseudo(ArrayList<String> liste, String pseudo){
        boolean contient = false;
        for(String p : liste){
            if (p.equals(pseudo)){
                contient = true;
                break;
            }
        }
        return contient;
    }

}